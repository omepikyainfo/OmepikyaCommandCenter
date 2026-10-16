import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class BenchmarkTests(unittest.TestCase):
    def test_benchmark_runs_on_small_corpus(self):
        with tempfile.TemporaryDirectory() as directory:
            corpus = Path(directory) / "corpus.txt"
            corpus.write_text(("abcabc abcabc. " * 20), encoding="utf-8")
            result = subprocess.run([sys.executable, "benchmark.py", "--text", str(corpus),
                                     "--steps", "3"], capture_output=True, text=True, check=True)
            self.assertIn("bigram_loss=", result.stdout)
            self.assertIn("context_mlp_loss=", result.stdout)

    def test_benchmark_rejects_tiny_corpus(self):
        with tempfile.TemporaryDirectory() as directory:
            corpus = Path(directory) / "tiny.txt"
            corpus.write_text("abc", encoding="utf-8")
            result = subprocess.run([sys.executable, "benchmark.py", "--text", str(corpus),
                                     "--steps", "2"], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)

if __name__ == "__main__":
    unittest.main()
