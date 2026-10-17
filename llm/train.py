"""Train Omepikya Tiny from random initialization."""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

from omepikya_tiny import CharacterTokenizer, OmepikyaTiny, split_text


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--text", default="data/sample.txt", help="UTF-8 training corpus")
    parser.add_argument("--steps", type=int, default=1500)
    parser.add_argument("--learning-rate", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--validation-fraction", type=float, default=0.1)
    parser.add_argument("--output", default="checkpoints/omepikya_tiny.npz")
    parser.add_argument("--log-every", type=int, default=100)
    args = parser.parse_args()

    if args.steps < 1 or args.log_every < 1:
        parser.error("--steps and --log-every must be positive")

    text = Path(args.text).read_text(encoding="utf-8")
    train_text, validation_text = split_text(text, args.validation_fraction)
    tokenizer = CharacterTokenizer.fit(train_text)
    train_ids = np.asarray(tokenizer.encode(train_text), dtype=np.int64)
    if validation_text:
        # A character not observed in training cannot be predicted by this vocabulary.
        validation_text = "".join(ch for ch in validation_text if ch in tokenizer.char_to_id)
        validation_ids = np.asarray(tokenizer.encode(validation_text), dtype=np.int64)
    else:
        validation_ids = np.asarray([], dtype=np.int64)

    if train_ids.size < 2:
        parser.error("Training text must contain at least two encodable characters.")
    model = OmepikyaTiny(len(tokenizer.characters), seed=args.seed,
                         learning_rate=args.learning_rate)
    inputs, targets = train_ids[:-1], train_ids[1:]
    print("Omepikya Tiny v0.1 — randomly initialized character bigram model")
    print(f"Characters: {len(tokenizer.characters)} | training pairs: {len(inputs)}")
    print(f"Parameters: {model.weights.size + model.bias.size:,} | steps: {args.steps}")
    for step in range(1, args.steps + 1):
        # Full-batch training is deterministic and memory-light for this tiny baseline.
        loss = model.train_step(inputs, targets)
        if step == 1 or step % args.log_every == 0 or step == args.steps:
            val_loss = float("nan")
            if validation_ids.size >= 2:
                val_loss = model.loss(validation_ids[:-1], validation_ids[1:])
            print(f"step={step:5d} train_loss={loss:.4f} val_loss={val_loss:.4f}")

    metadata = {
        "model_name": "Omepikya Tiny",
        "version": "0.1",
        "model_type": "character_bigram",
        "trained_from_scratch": True,
        "seed": args.seed,
        "training_steps": model.steps,
        "training_characters": len(train_text),
        "validation_fraction": args.validation_fraction,
        "training_data_path": str(args.text),
        "warning": "Tiny baseline; not a capable conversational assistant.",
    }
    model.save(args.output, tokenizer, metadata)
    print(f"Saved checkpoint: {args.output}")


if __name__ == "__main__":
    main()
