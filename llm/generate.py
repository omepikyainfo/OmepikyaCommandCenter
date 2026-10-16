"""Generate text from a saved Omepikya Tiny checkpoint."""
import argparse

from omepikya_tiny import OmepikyaTiny


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", default="checkpoints/omepikya_tiny.npz")
    parser.add_argument("--prompt", default="O")
    parser.add_argument("--tokens", type=int, default=200,
                        help="Number of characters to generate after the prompt")
    parser.add_argument("--temperature", type=float, default=0.8)
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()
    if args.tokens < 0:
        parser.error("--tokens must not be negative")
    model, tokenizer, metadata = OmepikyaTiny.load(args.checkpoint)
    if not args.prompt:
        parser.error("--prompt must not be empty")
    try:
        prompt_ids = tokenizer.encode(args.prompt)
    except ValueError as error:
        parser.error(str(error))
    generated = model.generate(prompt_ids[-1], args.tokens, seed=args.seed,
                               temperature=args.temperature)
    # Keep the user's complete prompt, then append generated continuation.
    continuation = tokenizer.decode(generated[1:])
    print(args.prompt + continuation)
    print(f"\n[model={metadata.get('model_name', 'unknown')}; steps={model.steps}]")

if __name__ == "__main__":
    main()
