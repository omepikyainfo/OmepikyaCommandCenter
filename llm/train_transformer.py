"""Train the tiny character-level causal Transformer from random initialization."""
from __future__ import annotations

import argparse
from pathlib import Path

import torch
import torch.nn.functional as F

from omepikya_transformer import OmepikyaTransformer, TransformerConfig, save_checkpoint


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--text", default="data/sample.txt")
    parser.add_argument("--steps", type=int, default=200)
    parser.add_argument("--block-size", type=int, default=32)
    parser.add_argument("--embedding-dim", type=int, default=32)
    parser.add_argument("--heads", type=int, default=2)
    parser.add_argument("--layers", type=int, default=1)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--learning-rate", type=float, default=0.001)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--output", default="checkpoints/omepikya_transformer.pt")
    args = parser.parse_args()
    if args.steps < 1 or args.batch_size < 1 or args.learning_rate <= 0:
        parser.error("steps, batch-size and learning-rate must be positive")

    text = Path(args.text).read_text(encoding="utf-8")
    vocabulary = "".join(sorted(set(text)))
    if len(text) < 8 or len(vocabulary) < 2:
        parser.error("training text must contain at least 8 characters and 2 unique characters")
    stoi = {char: index for index, char in enumerate(vocabulary)}
    encoded = torch.tensor([stoi[c] for c in text], dtype=torch.long)
    if len(encoded) <= args.block_size:
        args.block_size = max(2, len(encoded) - 1)

    torch.manual_seed(args.seed)
    config = TransformerConfig(
        vocab_size=len(vocabulary), block_size=args.block_size,
        embedding_dim=args.embedding_dim, heads=args.heads, layers=args.layers,
    )
    model = OmepikyaTransformer(config)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.learning_rate)
    model.train()
    for step in range(1, args.steps + 1):
        starts = torch.randint(0, len(encoded) - args.block_size, (args.batch_size,))
        x = torch.stack([encoded[s : s + args.block_size] for s in starts])
        y = torch.stack([encoded[s + 1 : s + args.block_size + 1] for s in starts])
        logits = model(x)
        loss = F.cross_entropy(logits.reshape(-1, config.vocab_size), y.reshape(-1))
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        if step == 1 or step % max(1, args.steps // 5) == 0:
            print(f"step={step} loss={loss.item():.4f}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    save_checkpoint(str(output), model, vocabulary)
    print(f"saved checkpoint: {output} (parameters={sum(p.numel() for p in model.parameters())})")


if __name__ == "__main__":
    main()
