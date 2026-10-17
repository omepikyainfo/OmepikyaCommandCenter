"""Generate characters from a trained Omepikya causal Transformer checkpoint."""
from __future__ import annotations

import argparse

import torch

from omepikya_transformer import load_checkpoint


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--prompt", default="")
    parser.add_argument("--tokens", type=int, default=100)
    parser.add_argument("--temperature", type=float, default=0.8)
    args = parser.parse_args()
    if args.tokens < 0:
        parser.error("tokens must be non-negative")
    model, vocabulary = load_checkpoint(args.checkpoint)
    stoi = {char: index for index, char in enumerate(vocabulary)}
    unknown = sorted(set(args.prompt) - set(stoi))
    if unknown:
        parser.error(f"prompt contains characters absent from training vocabulary: {unknown!r}")
    prompt_ids = [stoi[c] for c in args.prompt]
    if not prompt_ids:
        prompt_ids = [0]
    generated = model.generate(torch.tensor([prompt_ids], dtype=torch.long), args.tokens, args.temperature)
    print("".join(vocabulary[index] for index in generated[0].tolist()))


if __name__ == "__main__":
    main()
