__config() -> {
    'strict' -> true,
    'scope' -> 'global',
    'command_permission' -> 'ops',
    
    'commands' -> {
        'leaderboard' -> 'show_place_history',
        'advancement <count>' -> 'record_advancement',
        'advancement grant <count>' -> 'record_advancement',
        'advancement revoke <count>' -> 'revoke_advancement',
    },
    'arguments' -> {
        'count' -> {'type' -> 'int', 'min' -> 1}
    }
};
// Global Data
global_place_history_p = {};
global_place_history_d = {};
global_advancement = {};
get_date_string() -> (
    join('/', slice(convert_date(unix_time()), 0, 3));
);
update_place_history(plrName, blockName) -> (
    dateStr = get_date_string();
    // 按玩家名分类
    plrData = global_place_history_p:plrName;
    if (!plrData, plrData = { 'count' -> 0 });
    plrData += dateStr;
    plrData:dateStr = blockName;
    plrData:'count' += 1;
    global_place_history_p:plrName = plrData;
    write_file('place_history_by_player', 'json', encode_json(global_place_history_p));
    // 按日期分类
    dateData = global_place_history_d:dateStr;
    if (!dateData,
        dateData = { 'count' -> 0 };
        // 新的一天，所有方块days-1
        for (entity_selector('@e[tag=1day]'),
            modify(_, 'kill');
        );
        for (entity_selector('@e[tag=2days]'),
            modify(_, 'tag', '1day');
        );
    );
    dateData += plrName;
    dateData:plrName = blockName;
    dateData:'count' += 1;
    global_place_history_d:dateStr = dateData;
    write_file('place_history_by_date', 'json', encode_json(global_place_history_d));
    // 输出日志
    logger('[Place History] ' + plrName + ' placed ' + blockName + ' on ' + dateStr);
);

// Command Handlers
show_place_history() -> (
    player_list = sort_key(keys(global_place_history_p), -(global_place_history_p:_:'count'));
    for (player_list,
        print(player(), format('d ' + global_place_history_p:_:'count' + 
          if (global_place_history_p:_:'count' > 9, '   ', '    ') + _));
    )
);
record_advancement_server(name, count) -> (
    print(player(name), format('c 成功同步了' + count + '个关键进度！'));
    global_advancement:name = global_advancement:name + count;
    write_file('advancement_completion', 'json', encode_json(global_advancement));

    logger('[Advancement] ' + name + ' has completed ' + count + ' critical advancement(s). Total: ' + global_advancement:name);
);
record_advancement(count) -> (
    print(player(), format('c 你获得了' + count + '个关键进度！'));
    name = player()~'name';
    global_advancement:name = global_advancement:name + count;
    write_file('advancement_completion', 'json', encode_json(global_advancement));

    logger('[Advancement] ' + name + ' has completed ' + count + ' critical advancement(s). Total: ' + global_advancement:name);
);
revoke_advancement(count) -> (
    name = player()~'name';
    if (global_advancement:name != null && global_advancement:name > 0,
        global_advancement:name = max(0, global_advancement:name - count);
        write_file('advancement_completion', 'json', encode_json(global_advancement));
        print(player(), format('r 你的关键进度计数已被撤销' + count + '！'));
        logger('[Advancement] ' + name + ' had ' + count + ' critical advancement(s) revoked. Total: ' + global_advancement:name),
    // else
        print(player(), format('r 关键进度计数为零，无法撤销！'));
    );
);

// Events
__on_start() -> (
    place_history_file_p = read_file('place_history_by_player', 'json');
    global_place_history_p = if(place_history_file_p, decode_json(place_history_file_p), {});

    place_history_file_d = read_file('place_history_by_date', 'json');
    global_place_history_d = if(place_history_file_d, decode_json(place_history_file_d), {});

    global_advancement_file = read_file('advancement_completion', 'json');
    global_advancement = if(global_advancement_file, decode_json(global_advancement_file), {});

    if (scoreboard()~'advancements' == null,
        scoreboard_add('advancements');
    );
);
global_confirmed = false;
__on_player_placing_block(player, item_tuple, hand, block)->(
    player_data = global_place_history_p:(player~'name');
    // 检查今天是否已经放置过方块
    if (player_data && player_data:get_date_string(),
        print(player, format('y 今天你已经放置过方块了喵！'));
        return('cancel'),
    !global_confirmed,
        display_title(player, 'actionbar', format('d 再次放置方块以确认放置，切换快捷栏以取消'), 0, 100, 0);
        global_confirmed = true;
        return('cancel'),
    // else
        global_confirmed = false;
    )
);
__on_player_switches_slot(player, from, to) -> (
    if (global_confirmed,
        global_confirmed = false;
        display_title(player, 'actionbar', format('r 已取消确认'), 0, 100, 0);
    );
);
__on_player_places_block(player, item_tuple, hand, block) -> (
    data = nbt('{}');
    put(data, 'block_state.Name', block);
    for (keys(block_state(block)),
        put(data, 'block_state.Properties.' + _ , block_state(block):_ );
    );
    put(data, 'Glowing', true);
    put(data, 'transformation', {
        'left_rotation' -> {'angle' -> 0.0, 'axis' -> [1.0, 0.0, 0.0]},
        'right_rotation' -> {'angle' -> 0.0, 'axis' -> [1.0, 0.0, 0.0]},
        'scale' -> [0.98, 0.98, 0.98],
        'translation' -> [0.01, 0.0, 0.01]
    });
    block_entity = spawn('minecraft:block_display', pos(block), data);
    update_place_history(player~'name', str(block));
    // modify一定要放在update后面，否则tag会被覆盖
    modify(block_entity, 'tag', '2days');
);
__on_player_clicks_block(player, block, face) -> (
    if (player~'holds' == null && str(block) == 'dispenser',
        item = null;
        for (range(9), 
            if (inventory_get(block, _),
                item = inventory_get(block, _);
                break();
            );
        );
        if (item == null,
            // print('Dispenser is empty!');
            return();
        );
        
        // print('Try place in front of dispenser');
        // print(item);
        facing = block_state(block, 'facing');
        if (place_item(item:0, pos(block) + global_facing_vectors:facing, facing),
            inventory_remove(block, item:0, 1);
        );
    );
);
__on_tick() -> (
    if (tick_time() % 1200 == 0,
        for (scoreboard('advancements'), 
            if (scoreboard('advancements', _) > 0,
                record_advancement_server(_, scoreboard('advancements', _));
                scoreboard('advancements', _, 0);
            );
        );
    );
);
global_facing_vectors = {
    'north' -> [0, 0, -1],
    'south' -> [0, 0, 1],
    'west' -> [-1, 0, 0],
    'east' -> [1, 0, 0],
    'up' -> [0, 1, 0],
    'down' -> [0, -1, 0]
};