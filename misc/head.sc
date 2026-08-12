__config() -> {
    'scope' -> 'global',
    'commands' -> {
        '' -> ['get_head', null],
        '<name>' -> 'get_head'
    },
    'arguments' -> {
        'name' -> {'type' -> 'term'}
    }
};

get_head(name) -> (
    if(player()~'gamemode' != 'creative', return());
    if(!name || type(name) != 'string', name = player()~'name');
    run('give ' + player()~'command_name' + ' minecraft:player_head[minecraft:profile=' + name + ']');
    //run('execute as ' + player()~'command_name' + ' run summon minecraft:item ~ ~ ~ {PickupDelay:20s,Item:{id:"minecraft:player_head",components:{"minecraft:profile":' + name + '}}}');
)
