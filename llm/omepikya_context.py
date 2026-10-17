"""Context-aware character language model with a small NumPy MLP."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, Iterable, List, Tuple

import numpy as np

from omepikya_tiny import CharacterTokenizer


class OmepikyaContext:
    """Predict the next character from a fixed window of previous characters.

    The model uses learned embeddings, a tanh hidden layer, and a softmax output.
    It is randomly initialized and trained from scratch; it is not a Transformer.
    Token ``vocab_size`` is reserved for left-padding shorter contexts.
    """

    MODEL_TYPE = "omepikya_context_mlp_v1"

    def __init__(self, vocab_size: int, context_size: int = 4,
                 embedding_dim: int = 8, hidden_size: int = 32,
                 learning_rate: float = 0.05, seed: int = 7):
        if vocab_size < 2:
            raise ValueError("Vocabulary must contain at least two characters.")
        if context_size < 1 or embedding_dim < 1 or hidden_size < 1:
            raise ValueError("Context, embedding, and hidden sizes must be positive.")
        if learning_rate <= 0:
            raise ValueError("Learning rate must be positive.")
        self.vocab_size = int(vocab_size)
        self.context_size = int(context_size)
        self.embedding_dim = int(embedding_dim)
        self.hidden_size = int(hidden_size)
        self.learning_rate = float(learning_rate)
        rng = np.random.default_rng(seed)
        scale = 0.1
        self.embeddings = rng.normal(0, scale, (vocab_size + 1, embedding_dim))
        self.w_hidden = rng.normal(0, scale, (context_size * embedding_dim, hidden_size))
        self.b_hidden = np.zeros(hidden_size, dtype=np.float64)
        self.w_output = rng.normal(0, scale, (hidden_size, vocab_size))
        self.b_output = np.zeros(vocab_size, dtype=np.float64)
        self.steps = 0

    @staticmethod
    def _softmax(logits: np.ndarray) -> np.ndarray:
        shifted = logits - np.max(logits, axis=1, keepdims=True)
        exp = np.exp(shifted)
        return exp / np.sum(exp, axis=1, keepdims=True)

    def loss_and_gradients(self, contexts: np.ndarray, targets: np.ndarray
                           ) -> Tuple[float, Dict[str, np.ndarray]]:
        contexts = np.asarray(contexts, dtype=np.int64)
        targets = np.asarray(targets, dtype=np.int64).reshape(-1)
        if contexts.ndim != 2 or contexts.shape[1] != self.context_size:
            raise ValueError("Contexts must have shape (batch, context_size).")
        if contexts.shape[0] == 0 or contexts.shape[0] != targets.size:
            raise ValueError("Contexts and targets must have the same non-zero batch size.")
        if np.any(contexts < 0) or np.any(contexts > self.vocab_size):
            raise ValueError("Context token ID outside vocabulary including padding token.")
        if np.any(targets < 0) or np.any(targets >= self.vocab_size):
            raise ValueError("Target token ID outside vocabulary.")

        batch = contexts.shape[0]
        embedded = self.embeddings[contexts]
        flat = embedded.reshape(batch, -1)
        hidden_pre = flat @ self.w_hidden + self.b_hidden
        hidden = np.tanh(hidden_pre)
        logits = hidden @ self.w_output + self.b_output
        probabilities = self._softmax(logits)
        loss = -np.mean(np.log(np.maximum(probabilities[np.arange(batch), targets], 1e-12)))

        dlogits = probabilities.copy()
        dlogits[np.arange(batch), targets] -= 1.0
        dlogits /= batch
        gradients: Dict[str, np.ndarray] = {}
        gradients["w_output"] = hidden.T @ dlogits
        gradients["b_output"] = np.sum(dlogits, axis=0)
        dhidden = dlogits @ self.w_output.T
        dhidden_pre = dhidden * (1.0 - hidden * hidden)
        gradients["w_hidden"] = flat.T @ dhidden_pre
        gradients["b_hidden"] = np.sum(dhidden_pre, axis=0)
        dflat = dhidden_pre @ self.w_hidden.T
        dembedded = dflat.reshape(batch, self.context_size, self.embedding_dim)
        grad_embeddings = np.zeros_like(self.embeddings)
        np.add.at(grad_embeddings, contexts.reshape(-1), dembedded.reshape(-1, self.embedding_dim))
        gradients["embeddings"] = grad_embeddings
        return float(loss), gradients

    def train_step(self, contexts: np.ndarray, targets: np.ndarray) -> float:
        loss, gradients = self.loss_and_gradients(contexts, targets)
        for name, gradient in gradients.items():
            parameter = getattr(self, name)
            parameter -= self.learning_rate * gradient
        self.steps += 1
        return loss

    def loss(self, contexts: np.ndarray, targets: np.ndarray) -> float:
        return self.loss_and_gradients(contexts, targets)[0]

    def make_examples(self, token_ids: Iterable[int]) -> Tuple[np.ndarray, np.ndarray]:
        ids = [int(token_id) for token_id in token_ids]
        if len(ids) < 2:
            raise ValueError("At least two token IDs are needed to make training examples.")
        if any(token_id < 0 or token_id >= self.vocab_size for token_id in ids):
            raise ValueError("Training token ID outside vocabulary.")
        pad_id = self.vocab_size
        contexts, targets = [], []
        history: List[int] = []
        for token_id in ids:
            contexts.append(([pad_id] * max(0, self.context_size - len(history)) +
                             history[-self.context_size:]))
            targets.append(token_id)
            history.append(token_id)
        return np.asarray(contexts[1:], dtype=np.int64), np.asarray(targets[1:], dtype=np.int64)

    def generate(self, prompt_ids: Iterable[int], count: int, seed: int = 0,
                 temperature: float = 0.8) -> List[int]:
        prompt = [int(token_id) for token_id in prompt_ids]
        if not prompt or any(token_id < 0 or token_id >= self.vocab_size for token_id in prompt):
            raise ValueError("Prompt must contain at least one valid token ID.")
        if count < 0:
            raise ValueError("Token count must not be negative.")
        if temperature <= 0:
            raise ValueError("Temperature must be positive.")
        rng = np.random.default_rng(seed)
        result = list(prompt)
        pad_id = self.vocab_size
        for _ in range(count):
            history = result[-self.context_size:]
            context = [pad_id] * (self.context_size - len(history)) + history
            flat = self.embeddings[np.asarray(context, dtype=np.int64)].reshape(1, -1)
            hidden = np.tanh(flat @ self.w_hidden + self.b_hidden)
            probabilities = self._softmax((hidden @ self.w_output + self.b_output) / temperature)[0]
            result.append(int(rng.choice(self.vocab_size, p=probabilities)))
        return result

    def save(self, path: str | Path, tokenizer: CharacterTokenizer,
             metadata: Dict[str, object] | None = None) -> None:
        if len(tokenizer.characters) != self.vocab_size:
            raise ValueError("Tokenizer vocabulary size does not match model.")
        destination = Path(path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        np.savez_compressed(destination, embeddings=self.embeddings, w_hidden=self.w_hidden,
            b_hidden=self.b_hidden, w_output=self.w_output, b_output=self.b_output,
            vocab_size=np.array(self.vocab_size), context_size=np.array(self.context_size),
            embedding_dim=np.array(self.embedding_dim), hidden_size=np.array(self.hidden_size),
            learning_rate=np.array(self.learning_rate), steps=np.array(self.steps),
            tokenizer_json=np.array(tokenizer.to_json()),
            metadata_json=np.array(json.dumps(metadata or {}, ensure_ascii=False)),
            model_type=np.array(self.MODEL_TYPE))

    @classmethod
    def load(cls, path: str | Path) -> Tuple["OmepikyaContext", CharacterTokenizer, Dict[str, object]]:
        with np.load(Path(path), allow_pickle=False) as data:
            if str(data["model_type"].item()) != cls.MODEL_TYPE:
                raise ValueError("Unsupported or invalid context-model checkpoint.")
            model = cls(int(data["vocab_size"].item()), int(data["context_size"].item()),
                int(data["embedding_dim"].item()), int(data["hidden_size"].item()),
                float(data["learning_rate"].item()))
            for name in ("embeddings", "w_hidden", "b_hidden", "w_output", "b_output"):
                value = np.asarray(data[name], dtype=np.float64)
                if value.shape != getattr(model, name).shape:
                    raise ValueError("Checkpoint parameter shape mismatch: " + name)
                setattr(model, name, value.copy())
            model.steps = int(data["steps"].item())
            tokenizer = CharacterTokenizer.from_json(str(data["tokenizer_json"].item()))
            metadata = json.loads(str(data["metadata_json"].item()))
        if len(tokenizer.characters) != model.vocab_size or not isinstance(metadata, dict):
            raise ValueError("Checkpoint tokenizer or metadata is invalid.")
        return model, tokenizer, metadata
