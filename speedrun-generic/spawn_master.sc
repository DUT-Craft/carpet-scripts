__config()->{
    'strict'->true,
    'scope'->'global'
};

load_global_data()->(
    file = read_file('data','nbt');
    if(file,
        global_spawn_list=parse_nbt(file);
        if(global_spawn_list==null,global_spawn_list={});
    ,
        global_spawn_list={}
    )
);

save_global_data()->(
    write_file('data','nbt',encode_nbt(global_spawn_list));
);

check_global_data()->(
    if(global_spawn_list==null,load_global_data());
);

global_spawn_list=null;
entity_dies(entity, reason, player) ->
(
    if(entity~'dimension'=='the_end',return());
    name=entity~'type';
    log_mob(name);
);

__on_player_attacks_entity(player, entity) ->
(
    entity_event(entity, 'on_death', 'entity_dies', player);
    //entity_dies(entity, amount, source, source_entity, player)
);

log_mob(name)->(
    count=get(global_spawn_list,name)+1;
    scoreboard('spawn_count',name, count);
    put(global_spawn_list,name,count);

);
log_mob_ext(entity)->(
    entity_event(entity, 'on_death', 'entity_dies', null);

);
handle_event('log_mob','log_mob');
handle_event('log_mob_ext','log_mob_ext');

refresh_display()->(
    for(keys(global_spawn_list),
        count=get(global_spawn_list,_);
        scoreboard('spawn_count',_, count);
    );
);

__on_start()->(
    load_global_data();
    scoreboard_remove('spawn_count');
    scoreboard_add('spawn_count');
    scoreboard_property('spawn_count','display_name',format('y 魂量计'));
    scoreboard_display('sidebar','spawn_count');
    refresh_display();
);

__on_close()->(
    save_global_data();
);

__on_player_disconnects(player, reason)->(
    save_global_data();
);

randpos()->(
    pos=[0,0,0];
    while(pos:1<10,100,
        pos=[rand(200)-100,0,rand(200)-100];
        pos:1=top('terrain',pos);
    );
    pos
);

take_entity()->(
    first(global_spawn_list,
        count=get(global_spawn_list,_)-1;
        scoreboard('spawn_count',_, count);
        if(count<1,
            delete(global_spawn_list,_);
            scoreboard('spawn_count',_, null);
        ,
            put(global_spawn_list,_,count);
        );
        return(_);
    );
    return(null);
);

do_spawn()->(
    in_dimension('the_end',
        if(length(global_spawn_list)<1,return());
        entity_name=take_entity();
        if(entity_name==null,return(););
        spawn(entity_name,randpos(),{'PersistenceRequired'->true});
        schedule(1,'do_spawn');
    );
);

__on_player_changes_dimension(player, from_pos, from_dimension, to_pos, to_dimension)->(
    if(to_dimension=='the_end',
        refresh_display();
        do_spawn();
    );
);
//script in spawn_master run global_spawn_list={'horse'-> 1, 'piglin_brute'-> 5, 'silverfish'-> 10, 'iron_golem'-> 5, 'sheep'-> 9, 'creeper'-> 4, 'cow'-> 8, 'pig'-> 19, 'enderman'-> 5, 'zombie'-> 32, 'piglin'-> 18, 'hoglin'-> 6, 'bat'-> 1, 'villager'-> 1, 'cave_spider'-> 7, 'skeleton'-> 5, 'blaze'-> 16, 'chicken'-> 4, 'spider'-> 14,  'magma_cube'-> 2}