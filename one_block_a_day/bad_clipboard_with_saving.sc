__config()->{
    'strict'->true,
    'scope'->'player'
};

global_sel_point1 = null;
global_sel_point2 = null;
global_point1_cache = null;
global_save_tick = null;

// 提取选区内方块数据，返回durability
save_chunk(x, y, z, l, h, w, durability, playerName) ->
(
    chunk_start = [x, y, z];
    tag = nbt('{}');
    without_updates(
        volume(chunk_start, chunk_start + [l, h, w],
            cur_block = _;
            block_tag = nbt('{}');
            block_tag:'pos' = pos(cur_block)-chunk_start;
            block_tag:'block' = str(cur_block);
            property=block_state(cur_block);
            properties = keys(property);
            for(properties,
                block_tag:('prop.'+_) = '"'+block_state(cur_block, _)+'"';
            );
            if( (d = block_data(cur_block)),   
                block_tag:'data' = d;
            ); 
            put(tag, 'blocks', block_tag, -1);
            if (!air(pos(cur_block)),
                set(pos(cur_block), 'air');
                durability = durability - 1;
                if (durability < 0,
                    break();
                );
            );
        );
    );
    write_file('clipboard_' + playerName, 'nbt', tag);
    // 更新周围六面的方块
    chunk_start = [min(x, x+l), min(y, y+h), min(z, z+w)];
    [l, h, w] = [abs(l) + 1, abs(h) + 1, abs(w) + 1];
    update_area(chunk_start + [-1,0,0], [1,h,w]);
    update_area(chunk_start + [l,0,0], [1,h,w]);
    update_area(chunk_start + [0,-1,0], [l,1,w]);
    update_area(chunk_start + [0,h,0], [l,1,w]);
    update_area(chunk_start + [0,0,-1], [l,h,1]);
    update_area(chunk_start + [0,0,w], [l,h,1]);
    durability;
);
load_chunk(tag, x, y, z) ->
(
    blocks = tag:'blocks[]';
    if (type(blocks)!='list',
        blocks = [blocks];
    );
    chunk_start = [x, y, z];
    l = h = w = 0;
    without_updates(
        for(blocks, 
            block_data = parse_nbt(_);
            unparsed_tag = _;
            bpos = block_data:'pos'+chunk_start;
            bname = block_data:'block';
            props = block_data:'prop';
            prop_list = [];
            if ( props, for (keys(props),
               prop_list += _;
               prop_list += str(props:_);
            ));
            data = null;
            if (has(block_data, 'data'),
                data = nbt(unparsed_tag:'data');
            );
            set(bpos, bname, prop_list, data);
        
            l = max(l, abs(bpos:0));
            h = max(h, abs(bpos:1));
            w = max(w, abs(bpos:2));
        );
    );
    // 更新选区内+周围一圈的方块
    for(keys(tag),
        update(chunk_start+_);
    );
    chunk_start = [min(x, x+l), min(y, y+h), min(z, z+w)];
    [l, h, w] = [l + 1, h + 1, w + 1];
    update_area(chunk_start + [-1,0,0], [1,h,w]);
    update_area(chunk_start + [l,0,0], [1,h,w]);
    update_area(chunk_start + [0,-1,0], [l,1,w]);
    update_area(chunk_start + [0,h,0], [l,1,w]);
    update_area(chunk_start + [0,0,-1], [l,h,1]);
    update_area(chunk_start + [0,0,w], [l,h,1]);
);
// 更新一个区域，从start开始，沿size方向扩展
update_area(start, size) -> (
    for (range(size:0),
        x = _;
        for (range(size:1),
            y = _;
            for (range(size:2),
                point = start + [x,y,_];
                update(point);
            );
        );
    );
);

__on_player_clicks_block(player, block, face)->
(
    if (!(player~'holds':0 ~ '_hoe') || global_sel_point2 != null,
        return();
    );
    global_sel_point1 = pos(block);
    display_title(player, 'actionbar', '第一点已设置：'+str(global_sel_point1)+'，请右键设置第二点');
    global_sel_point2 = null;
);

__on_player_right_clicks_block(player, item_tuple, hand, block, face, hitvec)->
(
    if (hand != 'mainhand' || !(item_tuple:0 ~ '_hoe'),
        return();
    );
    if (global_sel_point1 == null,
        clipboard = read_file('clipboard_' + player~'name', 'nbt');
        if (!global_save_tick || clipboard == null,
            display_title(player, 'actionbar', '剪贴板为空，请先左键设置第一点');
            return();
        );
        if (tick_time() - global_save_tick < 20,
            display_title(player, 'actionbar', '有一秒冷却时间，请稍等');
            return();
        );
        
        load_chunk(clipboard, ...pos(block));
        display_title(player, 'actionbar', '剪贴板内容已粘贴');
        delete_file('clipboard_' + player~'name', 'nbt');
        return();
    );
    global_sel_point2 = pos(block);
    display_title(player, 'actionbar', '选区已保存到剪贴板，手持锄头Shift右键空气撤销');

    // 剪切选区并消耗耐久
    parsed_item_nbt = parse_nbt(player()~'holds':2);
    item_max_durability = global_max_durability:(item_tuple:0);
    if (!item_max_durability,
        print(player, format('r [ERROR]所用的锄头未在耐久列表中注册，无法计算耐久，请反馈此问题！'));
        return();
    );
    durability = null;
    damage = parsed_item_nbt:'components':'minecraft:damage';
    if (!damage,
        durability = item_max_durability;
        if (!parsed_item_nbt:'components',
            parsed_item_nbt:'components' = {};
        ),
    // else
        durability = item_max_durability - damage;
    );
    durability =
      save_chunk(...global_sel_point1, ...(global_sel_point2 - global_sel_point1), durability, player~'name');
    if (durability <= 0,        
        inventory_set(player, player~'selected_slot', 0),
    // else
        parsed_item_nbt:'components':'minecraft:damage' = item_max_durability - durability;
        inventory_set(player, player~'selected_slot', 1, item_tuple:0, encode_nbt(parsed_item_nbt));
    );
    
    global_save_tick = tick_time();
    global_point1_cache = copy(global_sel_point1);
    global_sel_point1 = null;
    global_sel_point2 = null;
);

__on_player_uses_item(player, item_tuple, hand)->
(
    if (hand != 'mainhand' || !(item_tuple:0 ~ '_hoe') || !(player~'sneaking') ||
      query(player, 'trace', 15) != null || global_point1_cache == null,
        return();
    );
    clipboard = read_file('clipboard_' + player~'name', 'nbt');
    if (clipboard == null,
        return();
    );
    
    load_chunk(clipboard, ...global_point1_cache);
    display_title(player, 'actionbar', '剪切已撤销');
    delete_file('clipboard_' + player~'name', 'nbt');
    global_point1_cache = null;
);

global_max_durability = {
    'wooden_hoe' -> 59,
    'stone_hoe' -> 131,
    'copper_hoe' -> 190,
    'iron_hoe' -> 250,
    'golden_hoe' -> 32,
    'diamond_hoe' -> 1561,
    'netherite_hoe' -> 2031
};