// ---------------- 可调参数 ----------------
global_gun_item = 'crossbow';           // 持枪物品 id
                                        // 持枪物品的 NBT 数据, 用于判断是否为持枪物品
global_gun_nbt = '{id:"minecraft:crossbow", components:{"minecraft:custom_data":{"buckshot_roulette":1b}}}';
                                        // 用于标记道具展示实体的 tag
global_br_entity_tag = 'buckshot_roulette';
global_max_health = 5;                  // 每人最大生命值
global_start_health = 4;                // 每人初始生命值
global_bullet_range = range(4, 9);      // 子弹数范围 [min, max)
global_max_consumables = 8;             // 每人最大道具数
                                        // 可用道具 id 列表
global_consumable_ids = ['magnifier', 'saw', 'beer', 'handcuffs', 'phone', 'adrenaline'];
global_consumable_items = ['spyglass', 'shears', 'potion', 'iron_chain', 'writable_book', 'golden_apple'];
                                        // 可用道具显示名称列表（与 id 列表一一对应）
global_consumable_names = ['放大镜', '手锯', '啤酒', '手铐', '手机', '肾上腺素'];
global_consumable_descriptions = ['查看当前子弹是实弹还是空弹', '本回合实弹伤害翻倍', '退出当前子弹（不消耗回合）',
                                  '跳过对方下一回合', '随机收到一条提示信息', '退出当前回合并回复1点血量'];

// ---------------- 全局配置 ----------------
__config() -> {
    'scope' -> 'global',
    'command_permission' -> 'ops',
    'commands' -> {
        '' -> 'br_help',
        'help' -> 'br_help',
        'start <first_player> <second_player>' -> ['br_start', global_start_health],
        'start <first_player> <second_player> <start_health>' -> 'br_start',
        'cancel' -> 'br_cancel',
        'test <arg>' -> 'br_test',
        'test add_consumable <consumable_id>' -> ['br_test_add_consumable', false],
        'test add_consumable <consumable_id> <bool>' -> 'br_test_add_consumable'
    },
    'arguments' -> {
        // 注意: 'players' 类型返回的是玩家名字字符串, 这里需要的是实体。
        'first_player' -> {'type' -> 'players', 'single' -> true},
        'second_player' -> {'type' -> 'players', 'single' -> true},
        'start_health' -> {'type' -> 'int', 'min' -> 1, 'max' -> global_max_health, 'suggest' -> [global_start_health]},
        'arg' -> {'type' -> 'text', 'options' -> 
         ['show_consumables_panel', 'close_consumables_panel', 'add_all_consumables',
          'clear_all_entities']},
        'consumable_id' -> {'type' -> 'term', 'options' -> global_consumable_ids}
    }
};

// ---------------- 全局状态 ----------------
global_game_state = 0;              // 游戏状态: 0 (idle), 1 (in progress)
global_rounds = 0;                  // 当前对局轮数
global_player_template = {          // 玩家对象模板
    'entity' -> null,               // scarpet 玩家对象
    'name' -> 'Name',
    'health' -> 0,
    'consumables' -> [],
    'consumables_panel' -> null,
    'last_selected_consumable_uuid' -> null
};
global_consumable_template = {      // 道具对象模板
    'owner' -> null,                // 道具所属玩家对象
    'index' -> 0,                   // 道具在此玩家的道具面板中的索引，注意这不是 consumables 数组的索引，而是面板上的位置索引
    'id' -> 'magnifier',            // 道具 id
    'display' -> null,              // 道具展示实体对象
    'interaction' -> null           // 道具展示实体对应的交互实体对象
};
global_consumables_panel_template = {
    'owner' -> null,                // 面板所属玩家对象
    'entities' -> [],               // 面板中展示实体列表，包括展示实体和交互实体
    'center' -> [0, 0, 0],          // 面板中心位置
    'rotation' -> 0                 // 面板旋转角度
};

global_player_entities = [];        // 当前对局玩家实体列表 [first, second]，应当只用于整体 print 或遍历等
global_players = [];                // 当前对局玩家对象列表 [first, second]
global_current_turn = 0;            // 当前回合玩家索引 (0/1)
global_bullet_count = 6;            // 本回合总共子弹数
global_real_bullet_count = 3;       // 本回合实弹数
global_fake_bullet_count = 3;       // 本回合空弹数
global_bullets = [];                // 当前剩余子弹状态列表 (true = 实弹, false = 空弹)

global_saw_used = false;            // 本回合是否使用过手锯道具
global_handcuffs_used = false;      // 本回合是否使用过手铐

// ---------------- 命令 ----------------
br_help() -> (
    p = player();
    print(p, format('c ========== 恶魔轮盘赌 =========='));
    print(p, format('w /buckshot_roulette start <对方> ', 'w - 开始对局'));
    print(p, format('w /buckshot_roulette cancel ', 'w - 结束对局'));
    print(p, format('w 持枪时: 低头=瞄准自己, 抬头=瞄准对方, 右键开枪。'));
);

br_start(first_name, second_name, start_health) -> (
    if (first_name == second_name,
        print(player(first_name), '不能与自己对战：' + first_name + ' == ' + second_name);
        return();
    );
    first = player(first_name);
    second = player(second_name);
    if (first == null || second == null, 
        print(player(first_name), '玩家不在线：' + first_name + ' / ' + second_name);
        return();
    );
    global_player_entities = [first, second];
    // TODO: 随机血量
    global_players = [create_player(first, start_health), create_player(second, start_health)];
    global_rounds = 0;
    announce('恶魔轮盘赌对局开始！');

    round_start(-1);

    global_game_state = 1;
);

br_cancel() -> (
    announce('恶魔轮盘赌对局结束！');
    if (global_game_state == 1,
        // 清除玩家身上的手枪物品
        for (global_players,
            clear_gun_items(_);
        );
    );
    for (global_players,
        close_consumables_panel(_);
    );
    // global_player_entities = [];
    // global_players = [];
    global_game_state = 0;
);

br_test(arg) -> (
    p = player();
    // 确保至少有一个玩家对象存在
    player = null;
    if (length(global_players) == 0 || global_players:0 == null || global_players:0:'entity' == null,
        player = copy(global_player_template);
        player:'entity' = p;
        global_players = [player],
    // else
        player = if (global_players:0:'entity'~'id' == p~'id', global_players:0, global_players:1);
    );
    if (arg == 'show_consumables_panel',
        show_consumables_panel(player, false),
    arg == 'close_consumables_panel',
        close_consumables_panel(player),
    arg == 'add_all_consumables',
        for (global_consumable_ids,
            add_consumable(player, _);
        ),
    arg == 'clear_all_entities',
        c = 0;
        for (entity_selector('@e[tag=' + global_br_entity_tag + ']'),
            modify(_, 'kill');
            c += 1;
        );
        print(player(), format('c 已清除 ' + c + ' 个游戏内实体')),
    // else
        print(player(), format('c 未知测试参数: ' + arg));
    );
);

br_test_add_consumable(id, recreate_panel) -> (
    p = player();
    // 确保至少有一个玩家对象存在
    player = null;
    if (length(global_players) == 0 || global_players:0 == null || global_players:0:'entity' == null,
        player = copy(global_player_template);
        player:'entity' = p;
        global_players = [player],
    // else
        player = if (global_players:0:'entity'~'id' == p~'id', global_players:0, global_players:1);
    );
    add_consumable(player, id);

    if (recreate_panel,
        show_consumables_panel(player, false);
    );
);

// --------------- 游戏逻辑函数 ----------------
// ---------------- 游戏主体 ------------------
create_player(entity, health) -> (
    p = copy(global_player_template);
    p:'entity' = entity;
    p:'name' = entity~'name';
    p:'health' = health;
    // p:'consumables' = [];
    // p:'consumables_panel' = null;
    return(p);
);

round_start(previous_turn) -> (
    global_rounds = global_rounds + 1;

    slots = [];
    for (global_players,
        clear_gun_items(_);
    );
    for (global_players,
        p = _:'entity';
        found_slot = false;
        for (range(0, 10),
            if (inventory_get(p, _) == null,
                slots += _;
                found_slot = true;
                break();
            )
        );
        if (!found_slot,
            print(p, format('c 你的快捷栏没有空位放置手枪, 已替换第一格，原物品为：' + inventory_get(p, 0)));
            slots += 0;
        );
    );
    for (range(2),
        p = global_players:_;
        slot = slots:_;
        give_gun_item(p, slot);
    );
    
    // 初始化子弹，其中实弹数为三角分布随机数，至少为 1，最多为总子弹数 - 1，并且限制前两轮的子弹上限
    // 注意此处使用 [range(expr)] 把 range 对象转换为列表，否则 rand_item 会报错
    range_list = [global_bullet_range];
    if (global_rounds == 1,
        global_bullet_count = rand_item(slice(range_list, 0, floor(length(range_list) / 3))),
    global_rounds == 2,
        global_bullet_count = rand_item(slice(range_list, 0, floor(length(range_list) * 2 / 3))),
    // else
        global_bullet_count = rand_item(range_list);
    );
    global_real_bullet_count = triangular_rand_int(1, global_bullet_count);
    global_fake_bullet_count = global_bullet_count - global_real_bullet_count;
    global_bullets = [];
    for (range(global_real_bullet_count), global_bullets += true);
    for (range(global_fake_bullet_count), global_bullets += false);
    shuffle(global_bullets);

    if (global_rounds != 1,
        for (global_players,
            c1 = rand_item(global_consumable_ids);
            c2 = rand_item(global_consumable_ids);
            add_consumable(_, c1);
            add_consumable(_, c2);
            if (_:'consumables_panel' != null,
                show_consumables_panel(_, true)
            );
            display_title(_:'entity', 'title',
             format('w 你获得了：', 'c ' + get_consumable_name_by_id(c1) + '和' + get_consumable_name_by_id(c2)), 10, 40, 10);
            another_player = global_players:(1 - _i):'entity';
            print(another_player, format('w 对方获得了道具：',
             'c ' + get_consumable_name_by_id(c1) + '和' + get_consumable_name_by_id(c2)));
        );
    );

    global_saw_used = false;
    global_handcuffs_used = false;
    // 若新游戏则随机决定先手玩家，否则由上回合的最后没开枪的玩家先手
    global_current_turn = if (previous_turn == -1, rand_int(2), 1 - previous_turn);
);

get_current_player() -> (
    return(global_players:global_current_turn)
);

get_next_player() -> (
    return(global_players:(1 - global_current_turn))
);

get_player_by_name(name) -> (
    for (global_players,
        if (_:'name' == name, return(_));
    );
    return(null);
);

give_gun_item(player, slot) -> (
    p = player:'entity';
    // 这里必须要先清空该格物品，否则会出现假物品卡格子
    inventory_set(p, slot, 0);
    inventory_set(p, slot, 1, global_gun_item, global_gun_nbt);
);

// 在快捷栏中获取第一把带有 NBT 的手枪，返回 [[id, count, nbt], index] 或 null
get_gun_item(player) -> (
    p = player:'entity';
    for (range(10),
        i = inventory_get(p, _);
        if (i == null || i:0 != global_gun_item, continue());
        
        i_nbt = parse_nbt(i:2);
        if (i_nbt && i_nbt:'components' && i_nbt:'components':'minecraft:custom_data' && i_nbt:'components':'minecraft:custom_data':'buckshot_roulette',
            print('Found gun in slot ' + _ + ': ' + i);
            return([i, _]);
        );
    );
    return(null);
);

// 在快捷栏中获取第所有带有 NBT 的手枪，返回 [[id, count, nbt], [index1, index2, ...]] 或 null
get_gun_items(player) -> (
    if (player == null || player:'entity' == null, return(null));
    p = player:'entity';
    indexes = [];
    gun_item = null;
    for (range(10),
        i = inventory_get(p, _);
        if (i == null || i:0 != global_gun_item, continue());
        
        i_nbt = parse_nbt(i:2);
        if (i_nbt && i_nbt:'components' && i_nbt:'components':'minecraft:custom_data' && i_nbt:'components':'minecraft:custom_data':'buckshot_roulette',
            indexes += _;
            gun_item = i;
        );
    );
    if (length(indexes) > 0,
        return([gun_item, indexes]),
    // else
        return(null);
    );
);

clear_gun_items(player) -> (
    if (player == null || player:'entity' == null, return());
    p = player:'entity';
    indexes = get_gun_items(player):1;
    if (indexes != null && length(indexes) > 0,
        for (indexes,
            inventory_set(p, _, 0);
        );
    );
);

get_target(current_player, next_player) -> (
    if (current_player:'entity'~'pitch' >= 10,
        return(current_player),
    // else
        return(next_player);
    );
);

// 开火，返回当前子弹是否为实弹 (true = 实弹, false = 空弹)
fire(target_player, self) -> (
    real = global_bullets:0;
    sp = self:'entity';
    if (real, 
        sound('minecraft:entity.generic.explode', sp~'pos');
        modify(target_player:'entity', 'effect', 'blindness', 40);
        damage = if(global_saw_used, 2, 1);
        target_player:'health' = max(target_player:'health' - damage, 0);
        run('damage ' + target_player:'name' + ' 0.01'),
    // else
        sound('minecraft:block.tripwire.attach', sp~'pos');
    );
    delete(global_bullets, 0);
    return(real);
);

// 检查本轮游戏状态，若有玩家血量归零则宣布胜利并结束对局，若子弹用尽则进入下一轮
// 返回 true 表示游戏继续，false 表示本轮结束
check_status() -> (
    pc = get_current_player();
    pn = get_next_player();
    if (pc:'health' <= 0,
        announce(pc:'name' + ' 血量归零，' + pn:'name' + ' 获胜！');
        br_cancel();
        return(false),
    pn:'health' <= 0,
        announce(pn:'name' + ' 血量归零，' + pc:'name' + ' 获胜！');
        br_cancel();
        return(false);
    );
    
    if (length(global_bullets) == 0,
        announce('本轮子弹已用尽，重新装弹！');
        round_start(global_current_turn);
        return(false);
    );

    return(true);
);

switch_turns(current_player, target_player, real) -> (
    if ((real || !name_equals(current_player, target_player)) && // 如果开枪者打自己且开空弹，则不切换回合
     !global_handcuffs_used,                                     // 如果手铐道具被使用，则不切换回合
        global_current_turn = 1 - global_current_turn;
    );
    global_saw_used = false;
    global_handcuffs_used = false;
);

update_action_bar(player, target_player, selected_consumable) -> (
    self_is_current = name_equals(player, get_current_player());
    display_title(player:'entity', 'actionbar', format( // 🖤💔🤍
     'r ' + '🖤' * player:'health' + '  ',
     'r ' + '|' * global_real_bullet_count, 'c ' + '|' * global_fake_bullet_count + '  ',
     'w 当前：',
     'c ' + if (self_is_current, '你的', '对方') + '回合  ',
     'w 选中' + if(selected_consumable != null, '道具', '目标') + '：',
     'c ' + if(selected_consumable != null, get_consumable_full_description_by_id(selected_consumable:'id'),
      target_player != null, target_player:'name', '无'))),
);

// --------------- 道具系统 ----------------
// 生成道具的物品展示实体，返回实体对象
spawn_consumable_entity(name, pos, rot) -> (
    // Minecraft 原版中，transformation 的 angle 是弧度制的右手定则正向旋转角度，因此需要先取弧度，再取负号
    rot_rad = -rad(rot);
    ed = spawn('item_display', pos, concat('{item:{id:"', name,
     '"}, interpolation_duration:0.5f, transformation:{right_rotation:{angle:', rot_rad,
     'f, axis:[0f, 1f, 0f]},scale:[0.5f, 0.5f, 0.5f], left_rotation:{angle:0f, axis:[0f, 1f, 0f]}, translation:[0f, 0f, 0f]}}'));
    ei = spawn('interaction', pos - [0, 0.2, 0], concat('{width:0.4f, height:0.4f, response:1b}'));
    modify(ed, 'tag', global_br_entity_tag);
    modify(ei, 'tag', global_br_entity_tag);
    return([ed, ei]);
);

// refresh 参数表示是否原地刷新道具面板
show_consumables_panel(player, refresh) -> (
    p = player:'entity';
    refresh = refresh && player:'consumables_panel' != null;
    // 以 y 为旋转轴，从 z+ 顺时针旋转的角度
    rot = if(refresh, player:'consumables_panel':'rotation', p~'yaw');
    rot_vec = [-sin(rot), 0, cos(rot)];
    center = if(refresh, player:'consumables_panel':'center', p~'pos' + [0, 1.5, 0] + rot_vec * 2);
    // uv 局部坐标系轴向，由于需要面向玩家，即旋转180度，取负
    u_vec = [-cos(rot), 0, -sin(rot)];
    v_vec = [0, 1, 0];
    
    // 确保道具面板只存在一个
    if (player:'consumables_panel' != null, close_consumables_panel(player));
    // 生成道具展示实体
    list = player:'consumables';
    entities = [];
    for (list,
        i = _:'index';
        c = center + u_vec * (i % 4 - 1.5) + v_vec * (floor(i / 4) - 1);
        [ed, ei] = spawn_consumable_entity(get_consumable_item_by_id(_:'id'), c, rot);
        _:'display' = ed;
        _:'interaction' = ei;
        entities += ed;
        entities += ei;
    );
    player:'consumables_panel' = copy(global_consumables_panel_template);
    player:'consumables_panel':'owner' = player;
    player:'consumables_panel':'entities' = entities;
    player:'consumables_panel':'center' = center;
    player:'consumables_panel':'rotation' = rot;
);

close_consumables_panel(player) -> (
    if (player:'consumables_panel' == null, return());
    for (player:'consumables_panel':'entities',
        modify(_, 'kill');
    );
    player:'consumables_panel' = null;
);

add_consumable(player, id) -> (
    p = player:'entity';
    if (length(player:'consumables') >= global_max_consumables,
        return();
    );
    c = copy(global_consumable_template);
    c:'owner' = player;
    
    // 把 index 设为最小不重复的整数
    t = [range(global_max_consumables)];
    for(player:'consumables',
        delete(t, t~(_:'index'));
    );
    c:'index' = t:0;

    c:'id' = id;
    player:'consumables' += c;
    // print(p, concat('添加道具：', get_consumable_name_by_id(id), ' (Index: ', c:'index', ')'));
);

get_consumable_name_by_id(id) -> (
    return(global_consumable_names:(global_consumable_ids~id));
);

get_consumable_full_description_by_id(id) -> (
    c_id = global_consumable_ids~id;
    return(concat(get_consumable_name_by_id(id), '：', global_consumable_descriptions:c_id));
);

get_consumable_item_by_id(id) -> (
    return(global_consumable_items:(global_consumable_ids~id));
);

get_selected_consumable(player) -> (
    p = player:'entity';
    c = query(p, 'trace', 7, 'entities');
    if (c == null || c~'type' != 'interaction', return(null));
    interaction_id = c~'id';

    for (player:'consumables',
        if (_:'interaction'~'id' == interaction_id,
            return(_);
        );
    );
    return(null);
);

use_consumable(player, consumable, next_player) -> (
    p = player:'entity';
    success = call('use_consumable_' + consumable:'id', player, consumable);
    if (!success, return(false));
    // 使用道具后从玩家的 consumables 列表中删除该道具，并更新面板
    player:'consumables' = filter(player:'consumables', _:'index' != consumable:'index');
    show_consumables_panel(player, true);
    print(next_player:'entity', format('w 对方使用了道具：', 'c ' + get_consumable_name_by_id(consumable:'id')));
    // print(p, concat('使用道具：', get_consumable_name_by_id(consumable:'id'), ' (Index: ', consumable:'index', ')'));
    return(true);
);

// -------------- 道具使用函数 --------------
use_consumable_magnifier(player, consumable) -> (
    sound('minecraft:block.glass.break', player:'entity'~'pos');
    display_title(player:'entity', 'title', format('w 当前子弹状态：', if(global_bullets:0, 'r 实弹', 'c 空弹')), 10, 40, 10);
    return(true);
);

use_consumable_saw(player, consumable) -> (
    if (global_saw_used,
        print(player:'entity', 'c 本回合已使用过手锯道具，无法再次使用');
        return(false);
    );
    sound('minecraft:block.beehive.shear', player:'entity'~'pos');
    global_saw_used = true;
    return(true);
);

use_consumable_beer(player, consumable) -> (
    sound('minecraft:entity.generic.drink', player:'entity'~'pos');
    display_title(global_player_entities, 'title', format('w ' + player:'name' + '退出一枚：', if(global_bullets:0, 'r 实弹', 'c 空弹')), 10, 40, 10);
    delete(global_bullets, 0);
    return(true);
);

use_consumable_handcuffs(player, consumable) -> (
    if (global_handcuffs_used,
        print(player:'entity', 'c 本回合已使用过手铐道具，无法再次使用');
        return(false);
    );
    sound('minecraft:block.chain.break', player:'entity'~'pos');
    global_handcuffs_used = true;
    return(true);
);

use_consumable_phone(player, consumable) -> (
    sound('minecraft:item.book.page_turn', player:'entity'~'pos');
    i = rand_int(length(global_bullets));
    display_title(player:'entity', 'title', format('w 第', 'c ' + (i + 1),
     'w 枚子弹将会是：', if(global_bullets:i, 'r 实弹', 'c 空弹')), 10, 40, 10);
    return(true);
);

use_consumable_adrenaline(player, consumable) -> (
    if (player:'health' >= global_max_health,
        print(player:'entity', 'c 你的血量已满，无法使用肾上腺素道具');
        return(false);
    );
    sound('minecraft:entity.player.levelup', player:'entity'~'pos');
    player:'health' = min(player:'health' + 1, global_max_health);
    // 假装开了枪一样切换回合，如果后面要在 switch_turns 添加其他判断可能需要修改这里的脏逻辑
    switch_turns(player, player, true);
    return(true);
);

// --------------- 工具函数 ----------------
// 返回 0 到 num - 1 的随机数
rand_int(num) -> (
    return(floor(rand(num)))
);

rand_item(list) -> (
    return(list:rand_int(length(list)));
);

// 生成一个三角分布的随机整数，范围在 [min, max)
triangular_rand_int(min, max) -> (
    u1 = rand(1);
    u2 = rand(1);
    t = (u1 + u2) / 2;
    return(floor(t * (max - min) + min));
);

concat(...args) -> (
    result = '';
    for (args, result += _);
    return(result);
);

normalized(vec) -> (
    length = 0;
    for (vec, length += _ ^ 2);
    length = sqrt(length);
    if (length == 0, return(vec));
    return (vec / length);
);

// 比较 2 个玩家对象的名字是否相同
name_equals(p1, p2) -> (
    return(p1:'name' == p2:'name');
);

// 向参与对局的所有玩家发送青色字消息
announce(message) -> (
    print(global_player_entities, format('c ' + message));
);

shuffle(list) -> (
    for (range(length(list) - 1, 0, -1),
        i = _;
        j = rand_int(_);
        temp = list:i;
        list:i = list:j;
        list:j = temp;
    );
    // print('子弹已重新洗牌: ' + list);
);

// ---------------- 事件函数 ----------------
__on_player_uses_item(player, item_tuple, hand) -> (
    // 确保物品是带有正确 NBT 数据的枪
    if (hand != 'mainhand' || global_game_state == 0 || item_tuple:0 != global_gun_item || query(player, 'name') != get_current_player():'name',
        return();
    );
    p = get_current_player();
    // 确保玩家没有选中道具
    if (p:'last_selected_consumable_uuid' != null, return());
    gun_nbt = parse_nbt(item_tuple:2);
    if (!gun_nbt || !gun_nbt:'components' || !gun_nbt:'components':'minecraft:custom_data' || !gun_nbt:'components':'minecraft:custom_data':'buckshot_roulette',
        return();
    );
    
    target_player = get_target(p, get_next_player());
    real = fire(target_player, p);
    // 仅当本轮游戏继续时才切换回合，防止连续切换两次
    if (check_status(),
        switch_turns(p, target_player, real);
    );
);

__on_player_drops_item(player) -> (
    if (global_game_state == 0, return());
    item_tuple = player~'holds';
    if (item_tuple == null || item_tuple:0 != global_gun_item, return());
    gun_nbt = parse_nbt(item_tuple:2);
    if (!gun_nbt || !gun_nbt:'components' || !gun_nbt:'components':'minecraft:custom_data' || !gun_nbt:'components':'minecraft:custom_data':'buckshot_roulette',
        return();
    );

    p = get_player_by_name(player~'name');
    if (p:'consumables_panel' == null,
        show_consumables_panel(p, false),
    // else
        close_consumables_panel(p);
    );
    give_gun_item(p, player~'selected_slot');
    'cancel';
);

__on_player_interacts_with_entity(player, entity, hand) -> (
    if (hand != 'mainhand' || global_game_state == 0, return());
    p = get_player_by_name(player~'name');
    cp = get_current_player();
    if (p:'consumables_panel' == null || !name_equals(p, cp), return());
    for (p:'consumables',
        if (_:'interaction'~'id' == entity~'id',
            use_consumable(p, _, get_next_player());
            
            if (length(global_bullets) == 0,
                announce('本轮子弹已用尽，重新装弹！');
                round_start(global_current_turn);
            );
            break();
        );
    );
);

__on_tick() -> (
    if (tick_time() % 10 != 0 || global_game_state == 0, return());
    // 仅当手持弩时才选中目标玩家
    cp = get_current_player();
    target_player = get_target(cp, get_next_player());
    item_tuple = cp:'entity'~'holds';
    if (item_tuple == null || item_tuple:0 != global_gun_item,
        target_player = null;
    );
    gun_nbt = parse_nbt(item_tuple:2);
    if (!gun_nbt || !gun_nbt:'components' || !gun_nbt:'components':'minecraft:custom_data' || !gun_nbt:'components':'minecraft:custom_data':'buckshot_roulette',
        target_player = null;
    );

    for (global_players,
        selected_consumable = get_selected_consumable(_);
        s_uuid = if (selected_consumable != null, selected_consumable:'display'~'uuid', null);
        l_uuid = _:'last_selected_consumable_uuid';
        if (l_uuid != null && l_uuid != s_uuid,
            run('data modify entity ' + l_uuid + ' Glowing set value 0b');
        );
        if (selected_consumable != null && l_uuid != s_uuid,
            run('data modify entity ' + s_uuid + ' Glowing set value 1b');
        );
        _:'last_selected_consumable_uuid' = s_uuid;
        update_action_bar(_, target_player, selected_consumable);
    );
    // 当选中道具时，不给玩家发光
    if (cp:'last_selected_consumable_uuid' != null, return());

    // 仅当手持弩时才给玩家发光
    if (target_player == null, return());
    modify(target_player:'entity', 'effect', 'glowing', 12);
);

__on_close() -> (
    br_cancel();
);