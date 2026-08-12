__config() -> {
    'scope' -> 'global',
    'command_permission' -> 'ops',
    'commands' -> {
        '' -> 'minesweeperHelp',
        'help' -> 'minesweeperHelp',
        'set <borderOrigin> <row> <column> <mineCount>' -> 'minesweeperSet',
        'sign_set <borderOrigin> <pos>' -> 'minesweeperSetSign',
        'start' -> 'minesweeperStart',
        'start safe' -> 'minesweeperStartSafe',
        'cancel' -> 'minesweeperCancel',
        'info' -> 'minesweeperInfo'
    },
    'arguments' -> {
        'borderOrigin' -> {'type' -> 'pos'},
        'row' -> {'type' -> 'int', 'min' -> 8, 'max' -> 32, 'suggest' -> [16]},
        'column' -> {'type' -> 'int', 'min' -> 8, 'max' -> 32, 'suggest' -> [16]},
        'mineCount' -> {'type' -> 'int', 'min' -> 1, 'suggest' -> [40]}
    }
};

global_materials = ['tnt','white_concrete', 'blue_concrete',
'green_concrete', 'red_concrete', 'cyan_concrete',
'yellow_concrete', 'magenta_concrete', 'brown_concrete', 'gray_concrete'];
global_mineMap = []; // 0 - unrevealed safe, 1 - mine, 2 - flagged safe, 3 - flagged mine, 4 - revealed safe (needn't check)
global_nearbyMineCount = []; // How many mines neighboring each cell, for mine cell, it's -1
global_origin = [];
global_row = 16;
global_column = 16;
global_mineCount = 40;
global_gameStatus = 0; // 0 - not started, 1 - first click waiting, 2 - playing
global_playerList = [];
global_revealedCount = 0;
global_flaggedCount = 0;
global_safeMode = false;
minesweeperStart() -> (
    if (!global_origin,
        print(player(), format('r 请先设置扫雷参数'));
        minesweeperHelp();
        return();
    );
    if (global_gameStatus != 0,
        print(player(), format('r 帮你把正在进行的扫雷游戏重新开始了喵~'));
        minesweeperCancel();
    );
    global_gameStatus = 1;
    global_revealedCount = 0;
    global_flaggedCount = 0;
    global_safeMode = false;
    players = entity_area('player',
     global_origin + [floor(global_row / 2), 1, floor(global_column / 2)],
     [global_row, 16, global_column]);
    updatePlayers(global_origin, global_row, global_column);
    print(players, format('d 扫雷，启动！'));

    generateArena(global_origin, global_row, global_column);
    clearNum();
    schedule(0, 'updateNum', global_origin, global_row, global_column)
);
minesweeperStartSafe() -> (
    minesweeperStart();
    global_safeMode = true;
);
minesweeperCancel() -> (
    if (global_gameStatus == 0,
        print(player(), format('r 没有正在进行的扫雷游戏喵~'));
        return();
    );
    global_gameStatus = 0;
    for(global_playerList, 
        print(_, format('d 扫雷游戏中止'));
    );
    volume(global_origin + [1, 0, 1], global_origin + [global_row, 0, global_column],
      set(_, 'white_concrete'));
    volume(global_origin + [1, 1, 1], global_origin + [global_row, 1, global_column],
      set(_, 'air'));
    clearNum()
);
minesweeperInfo() -> (
    print(player(), format('w [' + global_row + 'x' + global_column + '] ' + global_mineCount + '雷'));
    if (global_gameStatus == 0,
        return();
    );
    print(player(), format('d 剩余雷数：', 'w ' + (global_mineCount - global_flaggedCount)));
);
minesweeperHelp() -> (
    print(player(), format('c 扫雷游戏：'));
    print(player(), format('d /minesweeper ', 'e set ', 'w <borderOrigin> <row> <column> <mineCount> ', 'y - 设置扫雷参数'));
    print(player(), format('d /minesweeper ', 'e start ', 'y - 开始游戏'));
    print(player(), format('d /minesweeper ', 'e cancel ', 'y - 中止游戏'));
);
minesweeperSet(origin, row, column, mineCount) -> (
    minesweeperCancel();
    if (row < 8 || row > 32,
        print(player(), '行数要在 8 到 32 之间喵~');
        return(),
        column < 8 || column > 32,
        print(player(), '列数要在 8 到 32 之间喵~');
        return()
    );
    if (mineCount < 1 || mineCount > row * column * 0.5,
        print(player(), '雷数要在 1 到 ' + floor(row * column * 0.5) + ' 之间喵~');
        return();
    );
    global_origin = origin;
    global_row = row;
    global_column = column;
    global_mineCount = mineCount;
    print(player(), format('d 边框原点：', 'w ' + origin));
    print(player(), format('d 场地大小：', 'w ' + row + 'x' + column));
    print(player(), format('d 雷数：', 'w ' + mineCount));
);
generateArena(origin, row, column) -> (
    volume(origin, origin + [row + 1, 0, column + 1],
     set(_, 'obsidian'));
    volume(origin + [1, 0, 1], origin + [row, 0, column],
     set(_, 'light_gray_concrete'));
    volume(origin + [1, 1, 1], origin + [row, 1, column],
     set(_, 'air'));
    set(origin, 'magenta_glazed_terracotta')
);
shuffleMines(origin, row, column, mineCount, firstClickPos) -> (
    list = [range(row * column)];
    result = [];
    for (range(row),
        result += [range(column)];
    );
    result = result * 0;
    gnmc = result * 0; // global_nearbyMineCount

    for(range(row * column - 1, 0, -1),
        index = floor(rand(_));
        t = list:_;
        list:_ = list:index;
        list:index = t;
    );

    [fcx, fcz] = fromWorldPos(origin, firstClickPos);
    num = 0;
    for (range(-1, 2),
        i = _;
        for (range(-1, 2),
            szx = fcx + i;
            szz = fcz + _;
            if (szx >= 0 && szx < row && szz >= 0 && szz < column,
                safeCell = fromXZ(column, [szx, szz]);
                if (list~safeCell < mineCount,
                    targetIndex = row * column - 1 - num;
                    [tx, tz] = toXZ(column, targetIndex);
                    while (abs(tx - fcx) <= 1 && abs(tz - fcz) <= 1,
                        num = num + 1;
                        targetIndex = targetIndex - 1;
                        [tx, tz] = toXZ(column, targetIndex);
                    );
                    list:(list~safeCell) = list:(targetIndex);
                    num = num + 1;
                )
            );
        );
    );

    for(range(mineCount),
        index = list:_;
        [x, z] = toXZ(column, index);
        result:x:z = 1;
        gnmc:x:z = -1;
        for (range(-1, 2),
            i = _;
            for (range(-1, 2),
                j = _;
                if (i == 0 && j == 0,
                    continue());
                nx = x + i;
                nz = z + j;
                if (nx >= 0 && nx < row && nz >= 0 && nz < column && gnmc:nx:nz != -1,
                    gnmc:nx:nz = gnmc:nx:nz + 1;
                );
            );
        );
    );
    global_mineMap = result;
    global_nearbyMineCount = gnmc;
);
leftClickOn(mMap, gnmc, origin, row, column, pos) -> (
    [x, z] = fromWorldPos(origin, pos);
    if (mMap:x:z == 1,
        BOOM(origin, row, column, pos),
    mMap:x:z == 0,
        set(pos, global_materials:(gnmc:x:z + 1));
        if (mMap:x:z != 4,
            mMap:x:z = 4; // mark revealed safe
            global_revealedCount = global_revealedCount + 1;
            if (gnmc:x:z == 0,
                revealNeighbours(mMap, gnmc, origin, row, column, pos, true)
            )
        ),
    mMap:x:z == 2, // flagged safe
        switchFlagged(mMap, origin, pos);
        mMap:x:z = 0,
    mMap:x:z == 3, // flagged mine
        switchFlagged(mMap, origin, pos);
        mMap:x:z = 1;
    );
    updateNum(origin, row, column)
);
revealNeighbours(mMap, gnmc, origin, row, column, pos, isSafe) -> (
    for (range(-1, 2),
        i = _;
        for (range(-1, 2),
            j = _;
            if (i == 0 && j == 0, continue());
            newPos = pos + [i, 0, j];
            [x, z] = fromWorldPos(origin, newPos);
            if (withinMap(origin, row, column, newPos),
                if (!isSafe, 
                    if (mMap:x:z == 2 || mMap:x:z == 3,
                        continue(),
                    mMap:x:z == 1,
                        BOOM(origin, row, column, newPos);
                    )
                );
                set(newPos, global_materials:(gnmc:x:z + 1));
                if (mMap:x:z != 4,
                    global_revealedCount = global_revealedCount + 1;
                    if (gnmc:x:z == 0,
                        mMap:x:z = 4; // mark revealed safe
                        revealNeighbours(mMap, gnmc, origin, row, column, newPos, isSafe),
                    gnmc:x:z > 0,
                        mMap:x:z = 4; // mark revealed safe
                    )
                );
            );
        )
    );
);
switchFlagged(mMap, origin, pos) -> (
    // withInMap check should be done before calling this function
    [x, z] = fromWorldPos(origin, pos);
    if (mMap:x:z == 0 || mMap:x:z == 1,
        mMap:x:z = mMap:x:z + 2;
        set(pos + [0, 1, 0], 'crimson_button[face=floor]');
        global_flaggedCount = global_flaggedCount + 1,
    mMap:x:z == 2 || mMap:x:z == 3,
        mMap:x:z = mMap:x:z - 2;
        set(pos + [0, 1, 0], 'air');
        global_flaggedCount = global_flaggedCount - 1
    )
);
checkNearbyFlags(mMap, gnmc, origin, row, column, pos) -> (
    [x, z] = fromWorldPos(origin, pos);
    if (mMap:x:z != 4,
        return();
    );
    validFlagCount = 0;
    invalidFlagCount = 0;
    for (range(-1, 2),
        i = _;
        for (range(-1, 2),
            if (i == 0 && _ == 0, continue());
            nx = x + i;
            nz = z + _;
            if (nx >= 0 && nx < row && nz >= 0 && nz < column,
                if (mMap:nx:nz == 3 && gnmc:nx:nz == -1,
                    validFlagCount = validFlagCount + 1,
                mMap:nx:nz == 2,
                    invalidFlagCount = invalidFlagCount + 1
                )
            );
        );
    );
    if (validFlagCount == gnmc:x:z && invalidFlagCount == 0,
        revealNeighbours(mMap, gnmc, origin, row, column, pos, false),
    invalidFlagCount + validFlagCount == gnmc:x:z,
        BOOM(origin, row, column, pos);
    );
    updateNum(origin, row, column)
);
withinMap(origin, row, column, pos) -> (
    [x, y, z] = pos;
    !(x - origin:0 < 1 || x - origin:0 > row || y != origin:1 || z - origin:2 < 1 || z - origin:2 > column)
);
toWorldPos(origin, coord) -> ( // coord is [x,z], return [x,y,z]
    origin + [coord:0 + 1, 0, coord:1 + 1];
);
fromWorldPos(origin, pos) -> ( // pos is [x,y,z], return [x,z]
    [pos:0 - origin:0 - 1, pos:2 - origin:2 - 1];
);
toXZ(column, index) -> (
    [floor(index / column), index % column];
);
fromXZ(column, coord) -> (
    [x, z] = coord;
    x * column + z;
);
BOOM(origin, row, column, pos) -> (
    print(global_playerList, format('rb BOOM!\n游戏失败'));
    if (global_safeMode,
        return();
    );

    for(range(row), // 感觉比用volume方便
        i = _;
        for (range(column),
            set(toWorldPos(origin, [i, _]), global_materials:(global_nearbyMineCount:i:_ + 1))
        );
    );
    global_gameStatus = 0;
    // todo: highlight tnt at pos
    // some visual effects
);
checkWin(revealedCount, origin, row, column, mineCount) -> (
    if (revealedCount == row * column - mineCount,
        print(global_playerList, format('d 恭喜你扫出了所有雷喵~'));
        // some visual effects
        print(global_playerList,
         format('y F') + format('m I') + format('r R') + format('c E') + 
         format('l W') + format('t O') + format('d R') + format('p K') + format('v S'));
        global_gameStatus = 0;
    );
);
updatePlayers(origin, row, column) -> (
    list = entity_area('player', origin + [floor(row / 2), 1, floor(column / 2)], [row,16,column]);
    for (global_playerList,
        if (list~_ == null,
            delete(global_playerList, global_playerList~_);
            run('clear ' + _ + ' minecraft:wooden_sword[minecraft:can_break={blocks:light_gray_concrete}]');
            print(_, format('r 你离开了扫雷区域喵~')),
        //else
            delete(list, list~_)
        );
    );
    for (list,
        global_playerList += _;
        print(_, format('d 你进入了扫雷区域喵~'))
    );
);

global_bList = [];
global_numEntity = [];
global_numEntityBase = {
    'text' -> {'text' -> ''},
    'background' -> 0,
    'brightness' -> {'block' -> 15, 'sky' -> 15},
    'transformation' -> {
        'right_rotation' -> [-1, 0, 0, 1],
        'left_rotation' -> [0, -1, 0, 1],
        'translation' -> [0, 0, 0],
        'scale' -> [4, 4, 4]
    }
};
updateNum(origin, row, column)-> (
    c_for(i = 0, i < column, i += 1,
        bList = [];
        volume(origin + [1, 0, i + 1], origin + [row, 0, i + 1],
            bNum = global_materials~_ - 1;
            bList += if(bNum <= 0, '', bNum)
        );
        if(has(global_bList, i) && global_bList:i == bList, continue());
        global_bList:i = bList;

        n = 0;
        bList = sort_key(bList, n += 1); // invert
        eData = copy(global_numEntityBase);
        eData:'text':'text' = join('\n', bList);

        if(!has(global_numEntity, i) || global_numEntity:i~'removed',
            ePos = origin + [1, 1.001, 1.45 + i];
            e = entity_area('text_display', ePos, [0.1, 0.1, 0.1]):0;
            if(e,
                modify(e, 'pos', ePos);
                modify(e, 'nbt_merge', encode_nbt(eData));
                global_numEntity += e,
            //else
                global_numEntity += spawn('text_display', ePos, encode_nbt(eData))
            ),
        //else
            modify(global_numEntity:i, 'nbt_merge', encode_nbt(eData));
        );
    )
);
clearNum() -> (
    for(global_numEntity, modify(_, 'remove'))
);

minesweeperSetSign(origin, signPos) -> (
    if(!block_tags(signPos, 'all_signs'), print(player(), format('r 未找到告示牌')); return());
    data = parse_nbt(block_data(signPos):'front_text':'messages');
    if(type(data) != 'list', return());
    args = [];
    for(data,
        if(_i > 2, break());
        v = if(type(_) == 'map',
            number(_:'text'),
        type(_) != 'string',
            null,
        //else
            number(_)
        );
        if(v < 8, print(player(), format('r 提供的参数过小或不为数字')); return());
        args += floor(v)
    );
    for([0, 1], args:_ = min(args:_, 24));
    args:2 = min(args:2, floor(args:0 * args:1 * 0.25));
    minesweeperSet(origin, ...args);
);

// ULTIMATE MINESWEEPER NO-GUESS CHECKER -- WIP
global_cellsToCheck = [];
constriant(posList, mines) -> (
    {'posList' -> posList, 'mines' -> mines};
);
ctContains(c1, c2) -> ( // constraint c1 contains c2
    for (c2:'posList',
        if (c1:'posList'~_ == null,
            return(false);
        );
    );
    return(true);
);
ctEquals(c1, c2) -> (
    if (length(c1:'posList') != length(c2:'posList'),
        return(false);
    );
    for (c1:'posList',
        if (c2:'posList'~_ == null,
            return(false);
        );
    );
    return(true);
);
ctDifference(c1, c2) -> ( // c1 - c2
    resultPosList = [];
    for (c1:'posList',
        if (c2:'posList'~_ == null,
            resultPosList += _;
        );
    );
    {'posList' -> resultPosList, 'mines' -> (c1:'mines' - c2:'mines')};
);
simplifyConstraints(c1, c2) -> (
    if (ctEquals(c1, c2),
        return([c1]);
    );
    intersection = [];
    for (c1:'posList',
        if (c2:'posList'~_ != null,
            intersection += _;
        );
    );
    if (length(intersection) == 0,
        return([c1, c2]); // no intersection, cannot simplify
    );

    c1Diffc2 = ctDifference(c1, c2):'posList';
    c2Diffc1 = ctDifference(c2, c1):'posList';
    if (ctContains(c1, c2) || (length(c1Diffc2:'posList') == c1Diffc2:'mines'),
        return([c1Diffc2, c2]), // c1 - c2 can be solved
    ctContains(c2, c1) || (length(c2Diffc1:'posList') == c2Diffc1:'mines'),
        return([c1, c2Diffc1]), // c2 - c1 can be solved
    // else
        return([c1, c2]); // cannot simplify
    );
);

// events
__on_tick() -> (
    if (tick_time() % 100 != 0 || !global_origin, return());
    updatePlayers(global_origin, global_row, global_column);
    if (tick_time() % 300 != 0, return());
    add_chunk_ticket(global_origin + [0, 0, global_column / 2 + 0.5], 'portal', ceil(global_column / 32) + 1);
    if (global_gameStatus == 0, return());
);
__on_player_clicks_block(player, block, face) -> (
    if (global_gameStatus != 0 && global_playerList~player() != null && 
         withinMap(global_origin, global_row, global_column, pos(block)),
        if (global_gameStatus == 1,
            shuffleMines(global_origin, global_row, global_column, global_mineCount, pos(block));
            global_gameStatus = 2;
        );
        leftClickOn(global_mineMap, global_nearbyMineCount, global_origin, global_row, global_column, pos(block));
        checkWin(global_revealedCount, global_origin, global_row, global_column, global_mineCount);
        'cancel';
    );
);
__on_player_right_clicks_block(player, item_tuple, hand, block, face, hitvec) -> (
    if (global_gameStatus == 2 && hand == 'mainhand' && global_playerList~player() != null &&
     withinMap(global_origin, global_row, global_column, pos(block)),
        if (player()~'sneaking',
            checkNearbyFlags(global_mineMap, global_nearbyMineCount, global_origin, global_row, global_column, pos(block));
            checkWin(global_revealedCount, global_origin, global_row, global_column, global_mineCount),
        //else
            switchFlagged(global_mineMap, global_origin, pos(block))
        );
    );
);
__on_close() -> (
    minesweeperCancel();
    clearNum()
);