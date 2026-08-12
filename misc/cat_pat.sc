__config() -> {
    'scope' -> 'global',
    'event_priority' -> -10
};

global_cd = {
};

__on_player_interacts_with_entity(player, entity, hand)->(
    if(player~'holds' != null || !player~'sneaking', return());
    if(entity~'type' == 'cat',
        if(!global_cd:(entity~'id'),
            pos = slice(entity~'location', 0, 3);
            pos:1 += 0.3;
            particle('heart', pos, 5);
            if(rand(5),
                if(query(entity, 'nbt', 'Owner'), sound('entity.cat.ambient', pos, 0.7, 1, 'neutral'), sound('entity.cat.stray_ambient', pos, 0.7, 1, 'neutral'));
                global_cd:(entity~'id') = 15,
            //else
                sound('entity.cat.purr', pos, 0.8, 1, 'neutral');
                global_cd:(entity~'id') = 40
            );
        );
        return('cancel'),
    //else if
    entity~'type' == 'ocelot',
        if(!global_cd:(entity~'id'),
            pos = slice(entity~'location', 0, 3);
            pos:1 += 0.3;
            particle('heart', pos, 3);
            sound('entity.ocelot.ambient', pos, 1, 1, 'neutral');
            global_cd:(entity~'id') = 25
        );
        return('cancel')
    )
);

__on_player_deals_damage(player, amount, entity) -> (
    if((entity~'type' == 'cat' || entity~'type' == 'ocelot')
            && !global_cd:(entity~'id')
            //&& rand(3) < 2
            && entity~'health' > amount
            && query(entity, 'nbt', 'Owner') != query(player, 'nbt', 'UUID'),
        schedule(10, _(e) -> (
            pos = slice(e~'location', 0, 3);
            particle('angry_villager', pos, 2);
            sound('entity.cat.hiss', pos, 0.5, 1, 'neutral');
        ), entity);
        global_cd:(entity~'id') = 30
    )
);

__on_tick()->(
    for(keys(global_cd),
        if((global_cd:_ += -1) <= 0, delete(global_cd, _))
    )
)