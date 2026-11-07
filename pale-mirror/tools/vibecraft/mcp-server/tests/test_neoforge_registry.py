"""The connected mod registry must admit actual blocks and reject invalid states."""
import base64
import logging
from unittest.mock import AsyncMock, Mock

import pytest
from vibecraft import minecraft_items_loader as catalog
from vibecraft.tools.vision_tools import handle_capture_screenshot
from vibecraft.tools.build_tools import handle_build
from vibecraft.config import VibeCraftConfig


@pytest.fixture
def live_registry():
    names = set(catalog.valid_block_names)
    items = list(catalog.minecraft_items)
    properties = dict(catalog._live_properties)
    catalog.install_block_registry([
        {"name": "minecraft:stone", "properties": {}},
        {"name": "handcrafted:oak_chair", "properties": {"facing": ["north", "south", "east", "west"]}},
    ])
    yield
    catalog.valid_block_names.clear()
    catalog.valid_block_names.update(names)
    catalog.minecraft_items[:] = items
    catalog._live_properties.clear()
    catalog._live_properties.update(properties)


def test_mod_block_states_and_server_switch(live_registry):
    assert catalog.validate_block("handcrafted:oak_chair[facing=west]") is None
    assert catalog.validate_block("handcrafted:oak_chair[facing=up]")
    assert catalog.validate_block("handcrafted:oak_chair[unknown=true]")
    assert catalog.validate_block("handcrafted:oak_chair[facing=west,facing=north]")
    assert catalog.validate_block("missing:chair")
    assert catalog.validate_block("stone") is None
    catalog.install_block_registry([{"name": "minecraft:stone", "properties": {}}])
    assert catalog.validate_block("handcrafted:oak_chair")
    assert not any(item["name"].startswith("handcrafted:") for item in catalog.minecraft_items)


@pytest.mark.asyncio
async def test_screenshot_is_mcp_image_content():
    encoded = base64.b64encode(b"png-fixture").decode()
    bridge = AsyncMock()
    bridge.capture_screenshot_async.return_value = {
        "image": "data:image/png;base64," + encoded, "width": 64, "height": 32,
    }
    result = await handle_capture_screenshot({}, bridge, None, logging.getLogger(__name__))
    assert [part.type for part in result] == ["text", "image"]
    assert result[1].mimeType == "image/png"
    assert result[1].data == encoded
    assert encoded not in result[0].text


@pytest.mark.asyncio
async def test_missing_acknowledgement_does_not_claim_build_success(live_registry):
    bridge = Mock()
    bridge.send_command.return_value = "Command sent; no server acknowledgement captured. Read the affected region to verify."
    result = await handle_build({"commands": ["/setblock 0 100 0 stone"]}, bridge,
                                VibeCraftConfig(), logging.getLogger(__name__))
    assert "without server acknowledgement" in result[0].text
    assert "completed successfully" not in result[0].text
