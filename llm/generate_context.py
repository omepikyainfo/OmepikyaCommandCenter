"""Generate text using an Omepikya Context checkpoint."""
import argparse
from omepikya_context import OmepikyaContext


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", default="checkpoints/omepikya_context.npz")
    parser.add_argument("--prompt", default="Omepikya")
    parser.add_argument("--tokens", type=int, default=200)
    parser.add_argument("--temperature", type=float, default=0.8)
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()
    if args.tokens < 0 or args.temperature <= 0:
        parser.error("--tokens must be non-negative and --temperature must be positive")
    model, tokenizer, metadata = OmepikyaContext.load(args.checkpoint)
    try:
        prompt_ids = tokenizer.encode(args.prompt)
    except ValueError as error:
        parser.error(str(error))
    generated = model.generate(prompt_ids, args.tokens, seed=args.seed, temperature=args.temperature)
    print(tokenizer.decode(generated))
    print(f"\n[model={metadata.get('model_name', 'unknown')}; steps={model.steps}; context={model.context_size}]")

if __name__ == "__main__":
    main()
