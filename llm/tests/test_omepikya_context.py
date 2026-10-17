import tempfile
import unittest
from pathlib import Path
import numpy as np
from omepikya_tiny import CharacterTokenizer
from omepikya_context import OmepikyaContext


class OmepikyaContextTests(unittest.TestCase):
    def setUp(self):
        self.text = "abcabcabcabc"
        self.tokenizer = CharacterTokenizer.fit(self.text)
        self.ids = self.tokenizer.encode(self.text)
        self.model = OmepikyaContext(len(self.tokenizer.characters), context_size=3,
                                     embedding_dim=3, hidden_size=8, learning_rate=0.1, seed=2)
        self.contexts, self.targets = self.model.make_examples(self.ids)

    def test_examples_are_padded_and_aligned(self):
        self.assertEqual(self.contexts.shape, (len(self.ids)-1, 3))
        self.assertTrue(np.all(self.contexts[0, :2] == self.model.vocab_size))
        self.assertEqual(self.contexts[0, 2], self.ids[0])
        self.assertEqual(self.targets[0], self.ids[1])

    def test_training_reduces_loss(self):
        before = self.model.loss(self.contexts, self.targets)
        for _ in range(120):
            self.model.train_step(self.contexts, self.targets)
        self.assertLess(self.model.loss(self.contexts, self.targets), before)

    def test_gradient_matches_finite_difference(self):
        _, gradients = self.model.loss_and_gradients(self.contexts[:4], self.targets[:4])
        epsilon = 1e-5
        for name, index in (("w_output", (0, 0)), ("w_hidden", (0, 0)), ("embeddings", (0, 0))):
            parameter = getattr(self.model, name)
            original = parameter[index]
            parameter[index] = original + epsilon
            plus = self.model.loss(self.contexts[:4], self.targets[:4])
            parameter[index] = original - epsilon
            minus = self.model.loss(self.contexts[:4], self.targets[:4])
            parameter[index] = original
            numeric = (plus - minus) / (2 * epsilon)
            self.assertAlmostEqual(numeric, gradients[name][index], places=5, msg=name)

    def test_generation_is_seeded_and_uses_prompt(self):
        first = self.model.generate(self.ids[:2], 8, seed=9)
        second = self.model.generate(self.ids[:2], 8, seed=9)
        self.assertEqual(first, second)
        self.assertEqual(first[:2], self.ids[:2])
        self.assertEqual(len(first), 10)

    def test_checkpoint_round_trip(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "context.npz"
            self.model.save(path, self.tokenizer, {"version": "test"})
            restored, tokenizer, metadata = OmepikyaContext.load(path)
            self.assertEqual(restored.steps, 0)
            self.assertEqual(tokenizer.characters, self.tokenizer.characters)
            self.assertEqual(metadata["version"], "test")
            np.testing.assert_allclose(restored.embeddings, self.model.embeddings)
            np.testing.assert_allclose(restored.w_hidden, self.model.w_hidden)

    def test_invalid_temperature_rejected(self):
        with self.assertRaises(ValueError):
            self.model.generate(self.ids[:1], 2, temperature=0)

if __name__ == "__main__":
    unittest.main()
