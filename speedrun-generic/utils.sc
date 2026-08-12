__config() -> {
    'strict'->true,
    'scope'->'global',
    'command_permission'->'all',
    'commands' -> {
        'set_command <command>'->_(command)->set_command(player(), command),
    },
    'arguments' -> {'command' -> {'type' -> 'text'}}
};

__on_player_uses_item(player, item_tuple, hand)->(
    entity = query(player, 'trace', 10, 'entities');
    if (entity == null, return(););
    if (entity~'type' != 'command_block_minecart', return(););
    print(player,format('l 使用/utils set_command <command>设置命令'));
);

set_command(player, command)->(
    entity = query(player, 'trace', 10, 'entities');
    if (entity == null, 
        block = query(player, 'trace', 10, 'blocks');
        if (block == null, 
            print(player,format('r 请看向命令方块'));
            return();
        );
        if (block != 'command_block', 
            print(player,format('r 请看向命令方块'));
            return();
        );
        pos = pos(block);
        set(pos,'command_block',{}, nbt({'Command'->'"'+command+'"'}));
    );
    if (entity~'type' != 'command_block_minecart',
        print(player,format('r 请看向命令方块矿车'));
        return();
    );
    modify(entity, 'nbt_merge', nbt(str('{Command:"%s"}', command)));
    print(player,format('l 命令已设置'));
);

__on_player_right_clicks_block(player, item_tuple, hand, block, face, hitvec)->(
    if (block == 'command_block',
        print(player,format('l 请使用/utils set_command <command>设置命令'));
        return();
    );
    if (item_tuple:0 != 'command_block', return(););
    pos = pos(block);
    set(pos, 'command_block');
    if(hand == 'mainhand',
        inventory_set(player, player~'selected_slot', 0, 'air');
    ,
        inventory_set(player, 40, 0, 'air');
    );
    print(player,format('l 命令方块已设置'));
    print(player,format('l 请使用/utils set_command <command>设置命令'));
);
