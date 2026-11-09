#ifndef FRAMEFORGE_TILE_ENGINE_H
#define FRAMEFORGE_TILE_ENGINE_H

#include "frameforge_types.h"
#include <vector>

namespace frameforge {

class TileEngine {
public:
    static std::vector<TileRect> computeTiles(
        int imageWidth,
        int imageHeight,
        int tileSize,
        int overlapPadding
    );

    static int determineAutoTileSize(size_t availableRamBytes, int width, int height, int scale);

    static void blendTileIntoTarget(
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
    );
};

} // namespace frameforge

#endif // FRAMEFORGE_TILE_ENGINE_H
