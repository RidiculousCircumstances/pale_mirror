# VibeCraft MCP Server Tests

Automated tests using pytest framework.

## Running Tests

From the `mcp-server` directory:

```bash
# Run all tests
pytest tests/

# Run with verbose output
pytest tests/ -v

# Run specific test file
pytest tests/test_minecraft_item_search.py

# Run with coverage report
pytest tests/ --cov=src/vibecraft
```

## Test Organization

- `test_minecraft_item_search.py` - Tests for Minecraft item search functionality

## Adding New Tests

1. Create a new file `test_*.py` in this directory
2. Use pytest conventions:
   - Test classes: `class TestFeatureName`
   - Test methods: `def test_specific_behavior(self)`
3. Use assertions and pytest fixtures as needed
4. Run `pytest tests/` to verify

## Future Tests

Tests to be added:
- Furniture placement logic (currently manual script in `scripts/`)
- RCON connection and command execution (requires test server)
- Spatial analysis algorithms
- Pattern and template validation
- WorldEdit command sanitization

## Integration Tests

Some features require a live Minecraft server for full integration testing. These may be marked with `@pytest.mark.integration` and skipped in CI unless a test server is available.
