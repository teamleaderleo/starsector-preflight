# Contributing

## Development requirements

- JDK 17
- Maven 3.9 or newer

Run the verification suite:

```bash
mvn verify
```

## Performance changes

A performance pull request should include:

- The issue it addresses
- Before and after traces
- Raw benchmark runs
- First-build and repeat-launch numbers
- Peak-memory observations
- Compatibility and fallback behavior

## Compatibility

Avoid committing Starsector, Fast Rendering, mod, or other third-party proprietary binaries. Integration tests should use synthetic fixtures or user-supplied local paths excluded by `.gitignore`.
