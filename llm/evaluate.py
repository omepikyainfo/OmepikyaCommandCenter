"""Evaluate next-character cross-entropy on text within the checkpoint vocabulary."""
import argparse
import math
from pathlib import Path

import numpy as np

from omepikya_tiny import OmepikyaTiny


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", default="checkpoints/omepikya_tiny.npz")
    parser.add_argument("--text", default="data/sample.txt")
    args = parser.parse_args()
    model, tokenizer, metadata = OmepikyaTiny.load(args.checkpoint)
    text = Path(args.text).read_text(encoding="utf-8")
    encodable = "".join(ch for ch in text if ch in tokenizer.char_to_id)
    ids = np.asarray(tokenizer.encode(encodable), dtype=np.int64)
    if ids.size < 2:
        raise SystemExit("Evaluation text needs at least two characters in the model vocabulary.")
    loss = model.loss(ids[:-1], ids[1:])
    print(f"cross_entropy={loss:.4f}")
    print(f"perplexity={math.exp(min(loss, 50.0)):.4f}")
    print(f"evaluated_characters={ids.size}")
    print(f"model_version={metadata.get('version', 'unknown')}")

if __name__ == "__main__":
    main()
