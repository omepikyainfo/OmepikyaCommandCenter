"""Compare the bigram and context MLP on the same held-out character stream."""
from __future__ import annotations
import argparse
import math
from pathlib import Path
import numpy as np
from omepikya_tiny import CharacterTokenizer, OmepikyaTiny, split_text
from omepikya_context import OmepikyaContext


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--text", default="data/sample.txt")
    parser.add_argument("--steps", type=int, default=300)
    parser.add_argument("--learning-rate", type=float, default=0.05)
    parser.add_argument("--context-size", type=int, default=4)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()
    if args.steps < 1:
        parser.error("--steps must be positive")
    text = Path(args.text).read_text(encoding="utf-8")
    train_text, validation_text = split_text(text, 0.2)
    # Use a single vocabulary fitted only on training text to avoid validation leakage.
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
    # Evaluate both models on identical next-character pairs from held-out text.
    bigram_loss = bigram.loss(val_array[:-1], val_array[1:])
    val_contexts, val_targets = mlp.make_examples(validation_ids)
    mlp_loss = mlp.loss(val_contexts, val_targets)
    print("Held-out next-character cross-entropy (lower is better)")
    print(f"vocabulary_size={len(tokenizer.characters)} train_chars={len(train_ids)} validation_chars={len(validation_ids)}")
    print(f"bigram_loss={bigram_loss:.6f} bigram_perplexity={math.exp(min(bigram_loss, 50.0)):.4f}")
    print(f"context_mlp_loss={mlp_loss:.6f} context_mlp_perplexity={math.exp(min(mlp_loss, 50.0)):.4f}")
    print("Note: this is a tiny smoke corpus; results are not evidence of general language ability.")

if __name__ == "__main__":
    main()
