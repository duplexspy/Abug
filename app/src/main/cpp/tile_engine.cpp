#include "tile_engine.h"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace frameforge {

std::vector<TileRect> TileEngine::computeTiles(
    int imageWidth,
    int imageHeight,
    int tileSize,
    int overlapPadding
) {
    std::vector<TileRect> tiles;
    if (tileSize <= 0) tileSize = 128;
    if (overlapPadding < 0) overlapPadding = 16;

    int stepX = tileSize - overlapPadding * 2;
    int stepY = tileSize - overlapPadding * 2;
    if (stepX <= 0) stepX = tileSize / 2;
    if (stepY <= 0) stepY = tileSize / 2;

    for (int y = 0; y < imageHeight; y += stepY) {
        for (int x = 0; x < imageWidth; x += stepX) {
            TileRect tile;
            int x0 = std::max(0, x - overlapPadding);
            int y0 = std::max(0, y - overlapPadding);
            int x1 = std::min(imageWidth, x + stepX + overlapPadding);
            int y1 = std::min(imageHeight, y + stepY + overlapPadding);

            tile.x = x0;
            tile.y = y0;
            tile.width = x1 - x0;
            tile.height = y1 - y0;

            tile.pad_left = x - x0;
            tile.pad_top = y - y0;
            tile.pad_right = x1 - (x + stepX);
            tile.pad_bottom = y1 - (y + stepY);

            tiles.push_back(tile);
        }
    }
    return tiles;
}

int TileEngine::determineAutoTileSize(size_t availableRamBytes, int width, int height, int scale) {
    size_t fullFrameMem = (size_t)width * height * 4 * scale * scale * 4; // float buffer
    if (availableRamBytes >= fullFrameMem * 6) {
        return 512;
    } else if (availableRamBytes >= fullFrameMem * 3) {
        return 256;
    } else if (availableRamBytes >= fullFrameMem) {
        return 128;
    }
    return 64;
}

void TileEngine::blendTileIntoTarget(
    const uint8_t* tileData,
    int tileWidth,
    int tileHeight,
    int tileStride,
    uint8_t* targetData,
    int targetWidth,
    int targetHeight,
    int targetStride,
    const TileRect& rect,
    int scale,
    int overlapPadding
) {
    int scaledX = rect.x * scale;
    int scaledY = rect.y * scale;
    int scaledPad = overlapPadding * scale;

    for (int row = 0; row < tileHeight; ++row) {
        int dstY = scaledY + row;
        if (dstY < 0 || dstY >= targetHeight) continue;

        const uint8_t* srcRow = tileData + row * tileStride;
        uint8_t* dstRow = targetData + dstY * targetStride;

        for (int col = 0; col < tileWidth; ++col) {
            int dstX = scaledX + col;
            if (dstX < 0 || dstX >= targetWidth) continue;

            const uint8_t* srcPx = srcRow + col * 4;
            uint8_t* dstPx = dstRow + dstX * 4;

            // Compute edge weight for seam blending
            float weight = 1.0f;
            if (scaledPad > 0) {
                float distLeft = (float)col / (float)scaledPad;
                float distTop = (float)row / (float)scaledPad;
                float distRight = (float)(tileWidth - 1 - col) / (float)scaledPad;
                float distBottom = (float)(tileHeight - 1 - row) / (float)scaledPad;

                float minDist = std::min({distLeft, distTop, distRight, distBottom});
                if (minDist < 1.0f) {
                    // Smooth cosine window: 0.5 * (1 - cos(pi * t))
                    weight = 0.5f * (1.0f - std::cos(minDist * 3.14159265f));
                }
            }

            if (weight >= 0.999f) {
                dstPx[0] = srcPx[0];
                dstPx[1] = srcPx[1];
                dstPx[2] = srcPx[2];
                dstPx[3] = 255;
            } else if (weight <= 0.001f) {
                // keep existing
            } else {
                float invWeight = 1.0f - weight;
                dstPx[0] = (uint8_t)std::clamp((float)srcPx[0] * weight + (float)dstPx[0] * invWeight, 0.0f, 255.0f);
                dstPx[1] = (uint8_t)std::clamp((float)srcPx[1] * weight + (float)dstPx[1] * invWeight, 0.0f, 255.0f);
                dstPx[2] = (uint8_t)std::clamp((float)srcPx[2] * weight + (float)dstPx[2] * invWeight, 0.0f, 255.0f);
                dstPx[3] = 255;
            }
        }
    }
}

} // namespace frameforge
