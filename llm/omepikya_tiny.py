"""Tiny character-level neural language model implemented with NumPy only."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, Iterable, List, Tuple

import numpy as np


class CharacterTokenizer:
    """Deterministic character tokenizer fitted only on supplied training text."""

    def __init__(self, characters: Iterable[str] = ()) -> None:
        self.characters = sorted(set(characters))
        self.char_to_id = {char: i for i, char in enumerate(self.characters)}

    @classmethod
    def fit(cls, text: str) -> "CharacterTokenizer":
        if not text:
            raise ValueError("Training text must not be empty.")
        return cls(text)

    def encode(self, text: str) -> List[int]:
        unknown = sorted(set(text) - set(self.char_to_id))
        if unknown:
            raise ValueError("Text contains characters outside the training vocabulary: " +
                             repr("".join(unknown)))
        return [self.char_to_id[ch] for ch in text]

    def decode(self, ids: Iterable[int]) -> str:
        inverse = self.characters
        result = []
        for token_id in ids:
            if token_id < 0 or token_id >= len(inverse):
                raise ValueError("Token ID is outside the vocabulary.")
            result.append(inverse[token_id])
        return "".join(result)

    def to_json(self) -> str:
        return json.dumps(self.characters, ensure_ascii=False)

    @classmethod
    def from_json(cls, payload: str) -> "CharacterTokenizer":
        characters = json.loads(payload)
        if not isinstance(characters, list) or not all(
                isinstance(ch, str) and len(ch) == 1 for ch in characters):
            raise ValueError("Invalid tokenizer vocabulary in checkpoint.")
        if len(set(characters)) != len(characters):
            raise ValueError("Duplicate characters in tokenizer vocabulary.")
        return cls(characters)


class OmepikyaTiny:
    """A small neural bigram model: P(next character | current character).

    All parameters are initialized from a seeded random generator. There are no
    pretrained weights. This baseline is intentionally simpler than a Transformer.
    """

    def __init__(self, vocab_size: int, seed: int = 7, learning_rate: float = 0.1):
        if vocab_size < 2:
            raise ValueError("Vocabulary must contain at least two characters.")
        if learning_rate <= 0:
            raise ValueError("Learning rate must be positive.")
        self.vocab_size = int(vocab_size)
        self.learning_rate = float(learning_rate)
        rng = np.random.default_rng(seed)
        self.weights = rng.normal(0.0, 0.01, (vocab_size, vocab_size)).astype(np.float64)
        self.bias = np.zeros(vocab_size, dtype=np.float64)
        self.steps = 0

    @staticmethod
    def _softmax(logits: np.ndarray) -> np.ndarray:
        shifted = logits - np.max(logits, axis=-1, keepdims=True)
        exp = np.exp(shifted)
        return exp / np.sum(exp, axis=-1, keepdims=True)

    def loss_and_gradients(self, inputs: np.ndarray, targets: np.ndarray
                           ) -> Tuple[float, np.ndarray, np.ndarray]:
        inputs = np.asarray(inputs, dtype=np.int64).reshape(-1)
        targets = np.asarray(targets, dtype=np.int64).reshape(-1)
        if inputs.size == 0 or inputs.size != targets.size:
            raise ValueError("Inputs and targets must have the same non-zero length.")
        if np.any(inputs < 0) or np.any(inputs >= self.vocab_size):
            raise ValueError("Input token ID outside vocabulary.")
        if np.any(targets < 0) or np.any(targets >= self.vocab_size):
            raise ValueError("Target token ID outside vocabulary.")

        probabilities = self._softmax(self.weights[inputs] + self.bias)
        n = inputs.size
        loss = -np.mean(np.log(np.maximum(probabilities[np.arange(n), targets], 1e-12)))
        dlogits = probabilities
        dlogits[np.arange(n), targets] -= 1.0
        dlogits /= n
        grad_weights = np.zeros_like(self.weights)
        np.add.at(grad_weights, inputs, dlogits)
        grad_bias = np.sum(dlogits, axis=0)
        return float(loss), grad_weights, grad_bias

    def train_step(self, inputs: np.ndarray, targets: np.ndarray) -> float:
        loss, grad_weights, grad_bias = self.loss_and_gradients(inputs, targets)
        self.weights -= self.learning_rate * grad_weights
        self.bias -= self.learning_rate * grad_bias
        self.steps += 1
        return loss

    def loss(self, inputs: np.ndarray, targets: np.ndarray) -> float:
        return self.loss_and_gradients(inputs, targets)[0]

    def generate(self, start_id: int, count: int, seed: int = 0,
                 temperature: float = 1.0) -> List[int]:
        if start_id < 0 or start_id >= self.vocab_size:
            raise ValueError("Start token ID outside vocabulary.")
        if count < 0:
            raise ValueError("Token count must not be negative.")
        if temperature <= 0:
            raise ValueError("Temperature must be positive.")
        rng = np.random.default_rng(seed)
        current = int(start_id)
        result = [current]
        for _ in range(count):
            logits = (self.weights[current] + self.bias) / temperature
            probabilities = self._softmax(logits)
            current = int(rng.choice(self.vocab_size, p=probabilities))
            result.append(current)
        return result

    def save(self, path: str | Path, tokenizer: CharacterTokenizer,
             metadata: Dict[str, object] | None = None) -> None:
        destination = Path(path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        np.savez_compressed(
            destination,
            weights=self.weights,
            bias=self.bias,
            vocab_size=np.array(self.vocab_size),
            learning_rate=np.array(self.learning_rate),
            steps=np.array(self.steps),
            tokenizer_json=np.array(tokenizer.to_json()),
            metadata_json=np.array(json.dumps(metadata or {}, ensure_ascii=False)),
            model_type=np.array("omepikya_tiny_char_bigram_v1"),
        )

    @classmethod
    def load(cls, path: str | Path) -> Tuple["OmepikyaTiny", CharacterTokenizer, Dict[str, object]]:
        with np.load(Path(path), allow_pickle=False) as data:
            model_type = str(data["model_type"].item())
            if model_type != "omepikya_tiny_char_bigram_v1":
                raise ValueError("Unsupported or invalid model checkpoint.")
            vocab_size = int(data["vocab_size"].item())
            model = cls(vocab_size, learning_rate=float(data["learning_rate"].item()))
            weights = np.asarray(data["weights"], dtype=np.float64)
            bias = np.asarray(data["bias"], dtype=np.float64)
            if weights.shape != (vocab_size, vocab_size) or bias.shape != (vocab_size,):
                raise ValueError("Checkpoint parameter shapes do not match vocabulary.")
            model.weights = weights.copy()
            model.bias = bias.copy()
            model.steps = int(data["steps"].item())
            tokenizer = CharacterTokenizer.from_json(str(data["tokenizer_json"].item()))
            metadata = json.loads(str(data["metadata_json"].item()))
        if len(tokenizer.characters) != vocab_size:
            raise ValueError("Checkpoint tokenizer and model vocabulary sizes differ.")
        if not isinstance(metadata, dict):
            raise ValueError("Checkpoint metadata must be an object.")
        return model, tokenizer, metadata


def split_text(text: str, validation_fraction: float = 0.1) -> Tuple[str, str]:
    if not 0.0 <= validation_fraction < 1.0:
        raise ValueError("Validation fraction must be in [0, 1).")
    if len(text) < 2:
        raise ValueError("At least two characters are required.")
    if validation_fraction == 0:
        return text, ""
    split_at = int(len(text) * (1.0 - validation_fraction))
    split_at = min(max(split_at, 1), len(text) - 1)
    return text[:split_at], text[split_at:]
