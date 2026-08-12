__config() -> {
    'scope' -> 'global'
};
entity_load_handler('*', _(e, new) -> (
    if (new,
        modify(e, 'gravity', false);
    );
));
__on_tick()->(
    for (player('all'),
        if (inventory_get(_, 39)~'glass' == null,
            modify(_, 'air', _~'air' - 5);
            if (_~'air' <= -20,
                run('damage ' + _~'name' + ' 2 minecraft:drown');
                modify(_, 'air', 0);
            );
        )
    )
);
__on_start() -> (
    for (entity_list('*'), 
        modify(_, 'gravity', false);
    );
    for (player('all'),
        run('attribute ' + _~'name' + ' minecraft:block_break_speed base set 2');
        if (inventory_get(_, 39)~'glass' == null,
            run('item replace entity ' + _~'name' + ' armor.head with minecraft:glass[minecraft:enchantments={binding_curse:1}]')
        )
        // inventory_set(_, 39, 1, 'minecraft:glass{Enchantments:[{id:"minecraft:binding_curse",lvl:1s}]}');
    );
    run('gamerule fallDamage false');
);
__on_player_connects(player) -> (
    modify(player, 'gravity', false);
    run('attribute ' + player~'name' + ' minecraft:block_break_speed base set 2');
    if (inventory_get(player, 39)~'glass' == null,
        run('item replace entity ' + player~'name' + ' armor.head with minecraft:glass[minecraft:enchantments={binding_curse:1}]')
    )
);
__on_player_respawns(player)->(
    modify(player, 'gravity', false);
    run('attribute ' + player~'name' + ' minecraft:block_break_speed base set 2');
    if (inventory_get(player, 39)~'glass' == null,
        run('item replace entity ' + player~'name' + ' armor.head with minecraft:glass[minecraft:enchantments={binding_curse:1}]')
    )
);
__on_player_drops_item(player)->(
    if (query(player, 'holds', 'mainhand') == null, 
        return();
    );
    modify(player, 'motion', player~'look' * -0.5 + player~'motion');
);