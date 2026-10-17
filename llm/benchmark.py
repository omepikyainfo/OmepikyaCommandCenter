"""Compare tiny character models on the same held-out next-character stream."""
from __future__ import annotations
import argparse
import math
import time
from pathlib import Path
import numpy as np
import torch
import torch.nn.functional as F
from omepikya_tiny import CharacterTokenizer, OmepikyaTiny, split_text
from omepikya_context import OmepikyaContext
from omepikya_transformer import OmepikyaTransformer, TransformerConfig


def transformer_loss(model: OmepikyaTransformer, ids: np.ndarray, block_size: int) -> float:
    """Token-weighted next-character cross-entropy over a held-out stream."""
    if len(ids) < 2:
        raise ValueError("validation stream must contain at least two tokens")
    model.eval()
    total_loss, total_tokens = 0.0, 0
    with torch.no_grad():
        for start in range(0, len(ids) - 1, block_size):
            end = min(start + block_size, len(ids) - 1)
            x = torch.tensor(ids[start:end], dtype=torch.long).unsqueeze(0)
            y = torch.tensor(ids[start + 1 : end + 1], dtype=torch.long)
            logits = model(x)[0]
            total_loss += F.cross_entropy(logits, y, reduction="sum").item()
            total_tokens += len(y)
    return total_loss / total_tokens


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--text", default="data/sample.txt")
    parser.add_argument("--steps", type=int, default=300, help="training steps for bigram and context MLP")
    parser.add_argument("--learning-rate", type=float, default=0.05)
    parser.add_argument("--context-size", type=int, default=4)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--transformer-steps", type=int, default=100)
    parser.add_argument("--block-size", type=int, default=16)
    parser.add_argument("--embedding-dim", type=int, default=16)
    parser.add_argument("--heads", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=8)
    args = parser.parse_args()
    if args.steps < 1 or args.transformer_steps < 1 or args.batch_size < 1:
        parser.error("training steps and batch-size must be positive")
    if args.block_size < 2:
        parser.error("--block-size must be at least 2")

    text = Path(args.text).read_text(encoding="utf-8")
    train_text, validation_text = split_text(text, 0.2)
    tokenizer = CharacterTokenizer.fit(train_text)
    train_ids = tokenizer.encode(train_text)
    validation_text = "".join(ch for ch in validation_text if ch in tokenizer.char_to_id)
    validation_ids = tokenizer.encode(validation_text)
    if len(train_ids) < 3 or len(validation_ids) < 2:
        raise SystemExit("Corpus must provide at least three training and two validation characters.")
    train_array = np.asarray(train_ids, dtype=np.int64)
    val_array = np.asarray(validation_ids, dtype=np.int64)

    bigram = OmepikyaTiny(len(tokenizer.characters), seed=args.seed, learning_rate=0.1)
    for _ in range(args.steps):
        bigram.train_step(train_array[:-1], train_array[1:])
    mlp = OmepikyaContext(len(tokenizer.characters), context_size=args.context_size,
        embedding_dim=8, hidden_size=32, learning_rate=args.learning_rate, seed=args.seed)
    train_contexts, train_targets = mlp.make_examples(train_ids)
    for _ in range(args.steps):
        mlp.train_step(train_contexts, train_targets)
    bigram_loss = bigram.loss(val_array[:-1], val_array[1:])
    val_contexts, val_targets = mlp.make_examples(validation_ids)
    mlp_loss = mlp.loss(val_contexts, val_targets)

    torch.manual_seed(args.seed)
    block_size = min(args.block_size, len(train_ids) - 1)
    config = TransformerConfig(vocab_size=len(tokenizer.characters), block_size=block_size,
        embedding_dim=args.embedding_dim, heads=args.heads, layers=1)
    transformer = OmepikyaTransformer(config)
    optimizer = torch.optim.AdamW(transformer.parameters(), lr=0.001)
    encoded_train = torch.tensor(train_ids, dtype=torch.long)
    start_time = time.perf_counter()
    transformer.train()
    for _ in range(args.transformer_steps):
        starts = torch.randint(0, len(encoded_train) - block_size, (args.batch_size,))
        x = torch.stack([encoded_train[s : s + block_size] for s in starts])
        y = torch.stack([encoded_train[s + 1 : s + block_size + 1] for s in starts])
        logits = transformer(x)
        loss = F.cross_entropy(logits.reshape(-1, config.vocab_size), y.reshape(-1))
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(transformer.parameters(), 1.0)
        optimizer.step()
    train_seconds = time.perf_counter() - start_time
    transformer_loss_value = transformer_loss(transformer, val_array, block_size)
    parameter_count = sum(parameter.numel() for parameter in transformer.parameters())

    print("Held-out next-character cross-entropy (lower is better)")
    print(f"vocabulary_size={len(tokenizer.characters)} train_chars={len(train_ids)} validation_chars={len(validation_ids)}")
    print(f"bigram_loss={bigram_loss:.6f} bigram_perplexity={math.exp(min(bigram_loss, 50.0)):.4f}")
    print(f"context_mlp_loss={mlp_loss:.6f} context_mlp_perplexity={math.exp(min(mlp_loss, 50.0)):.4f}")
    print(f"transformer_loss={transformer_loss_value:.6f} transformer_perplexity={math.exp(min(transformer_loss_value, 50.0)):.4f}")
    print(f"transformer_parameters={parameter_count} transformer_train_seconds={train_seconds:.3f} transformer_steps={args.transformer_steps}")
    print("Note: tiny smoke-corpus metrics are diagnostic only, not evidence of general language ability.")


if __name__ == "__main__":
    main()
