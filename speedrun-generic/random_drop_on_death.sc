__config() -> {
    'strict'->true,
    'scope'->'global',
};

random_drop(player)->(
    slot=floor(rand(41));
    item=inventory_get(player,slot);
    if(item==null,return(false););
    print(player,format('l 你掉落了物品：'+item_display_name(item)));
    drop_item(player,slot)>0;
);

__on_player_dies(player)->(
    if(!inventory_has_items(player),return(););
    max_try=3;
    for(range(max_try),
        if(random_drop(player),return(););
    );
    for(range(41),
        item=inventory_get(player,_);
        if(item==null,continue(););
        print(player,format('l 你掉落了物品：'+item_display_name(item)));
        drop_item(player,_);
        return();
    );
);

__on_player_connects(player)->(
    print(player,format('l 注意：死亡会随机掉落一组物品'));
);
