global_shouldPaste = false;
global_firstPos = [];
global_secondPos = [];
global_dimension = '';

safePaste(plr, clickPos) -> (
    [x1, y1, z1] = global_firstPos;
    [x2, y2, z2] = global_secondPos;
    [cx, cy, cz] = clickPos;
    if (abs(x1 - x2) * abs(y1 - y2) * abs(z1 - z2) > 32768,
        print(plr, '选区太大啦，为了避免服务器爆炸，放你一马');
        return();
    );
    offset = clickPos - global_firstPos;

    if (cx >= min(x1, x2) && cx <= max(x1, x2) &&
        cy >= min(y1, y2) && cy <= max(y1, y2) &&
        cz >= min(z1, z2) && cz <= max(z1, z2),
        global_firstPos <> global_secondPos; // swap
    );
    
    [x1, y1, z1] = global_firstPos; // reset
    [x2, y2, z2] = global_secondPos;
    stepX = if (x1 <= x2, 1, -1);
    stepY = if (y1 <= y2, 1, -1);
    stepZ = if (z1 <= z2, 1, -1);
    for (range(x1, x2 + stepX, stepX),
        px = _;
        for (range(y1, y2 + stepY, stepY),
            py = _;
            for (range(z1, z2 + stepZ, stepZ),
                pz = _;
                srcPos = [px, py, pz];
                destPos = [px, py, pz] + offset;
                set(destPos, block(srcPos));
                set(srcPos, 'air');
            );
        )
    );
);

__on_player_clicks_block(player, block, face) -> (
    global_firstPos = pos(block);
    global_dimension = player~'dimension';
);
__on_player_right_clicks_block(player, item_tuple, hand, block, face, hitvec) -> (
    if (hand != 'mainhand', 
        return();
    );

    if (global_shouldPaste,
        if (!global_firstPos, // global_secondPos should always be set here
            return();
        );
        safePaste(player, pos(block));
        global_firstPos = [];
        global_secondPos = [];
        global_shouldPaste = false,
    player~'dimension' == global_dimension,
        global_secondPos = pos(block);
        global_shouldPaste = true;
    );
);