__config() -> {
    'scope' -> 'global',
    'commands' -> {'' -> 'hat'}
};

hat()->(
    if(!player()~'insta_build' && query(player(), 'holds', 'head') || query(player(), 'has_scoreboard_tag', 'disable_scarpet_hat'), return());
    run('item replace entity @s armor.head from entity @s weapon.mainhand {function:"set_count",count:1}');
    if(!player()~'insta_build', run('item modify entity @s weapon {function:"set_count",count:-1,add:true}'))
)