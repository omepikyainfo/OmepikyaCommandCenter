"""Train Omepikya Context from random initialization."""
from __future__ import annotations
import argparse
from pathlib import Path
import numpy as np
from omepikya_tiny import CharacterTokenizer, split_text
from omepikya_context import OmepikyaContext


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--text", default="data/sample.txt")
    parser.add_argument("--steps", type=int, default=500)
    parser.add_argument("--learning-rate", type=float, default=0.05)
    parser.add_argument("--context-size", type=int, default=4)
    parser.add_argument("--embedding-dim", type=int, default=8)
    parser.add_argument("--hidden-size", type=int, default=32)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--validation-fraction", type=float, default=0.1)
    parser.add_argument("--output", default="checkpoints/omepikya_context.npz")
    parser.add_argument("--log-every", type=int, default=50)
    args = parser.parse_args()
    if args.steps < 1 or args.log_every < 1:
        parser.error("--steps and --log-every must be positive")
    text = Path(args.text).read_text(encoding="utf-8")
    train_text, validation_text = split_text(text, args.validation_fraction)
    tokenizer = CharacterTokenizer.fit(train_text)
    train_ids = tokenizer.encode(train_text)
    validation_text = "".join(ch for ch in validation_text if ch in tokenizer.char_to_id)
    validation_ids = tokenizer.encode(validation_text)
    model = OmepikyaContext(len(tokenizer.characters), args.context_size, args.embedding_dim,
                            args.hidden_size, args.learning_rate, args.seed)
    contexts, targets = model.make_examples(train_ids)
    if len(validation_ids) >= 2:
        val_contexts, val_targets = model.make_examples(validation_ids)
    else:
        val_contexts = val_targets = None
    print(f"Omepikya Context MLP | vocab={model.vocab_size} context={model.context_size} "
          f"parameters={sum(getattr(model, n).size for n in ('embeddings','w_hidden','b_hidden','w_output','b_output')):,}")
    for step in range(1, args.steps + 1):
        loss = model.train_step(contexts, targets)
        if step == 1 or step % args.log_every == 0 or step == args.steps:
            val_loss = model.loss(val_contexts, val_targets) if val_contexts is not None else float('nan')
            print(f"step={step:5d} train_loss={loss:.4f} val_loss={val_loss:.4f}")
    metadata = {"model_name": "Omepikya Context", "version": "0.2", "model_type": "context_mlp",
        "trained_from_scratch": True, "seed": args.seed, "training_steps": model.steps,
        "context_size": args.context_size, "embedding_dim": args.embedding_dim,
        "hidden_size": args.hidden_size, "training_characters": len(train_text),
        "validation_fraction": args.validation_fraction,
        "warning": "Small experimental model; not a capable conversational assistant."}
    model.save(args.output, tokenizer, metadata)
    print(f"Saved checkpoint: {args.output}")

if __name__ == "__main__":
    main()
