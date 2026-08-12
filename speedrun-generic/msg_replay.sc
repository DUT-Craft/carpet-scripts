// 消息回放系统 - 支持个人消息存储和全局公告功能

// 全局消息历史记录（最多100条）
global_msg_history = [];
// 全局公告存储
global_announcements = {};
// 未保存更改计数
global_unsaved_changes = 0;

__config() -> {
    'strict'->true,
    'scope'->'global',

    'commands' -> {
        'set <index> <message>' -> 'set_message',
        'clear <index>' -> 'clear_message',
        'set_global <message>' -> 'set_announcements',
        'clear_global' -> 'clear_announcements'
    },
    'arguments' -> {
        'index' -> {'type' -> 'int', 'min' -> 0, 'max' -> 9, 'suggest' -> [0,1,2,3,4,5,6,7,8,9]},
        'message' -> {'type' -> 'text'}
    }
};

// 保存全局数据（消息历史+公告）
save_global_data() -> (
    write_file('msg_history', 'json', encode_json(global_msg_history));
    write_file('global_announcements', 'json', encode_json(global_announcements));
    global_unsaved_changes = 0;
);

// 脚本加载时初始化
__on_start() -> (
    // 加载消息历史
    msg_history_file = read_file('msg_history', 'json');
    global_msg_history = if(msg_history_file,decode_json(msg_history_file),[]);
    // 加载全局公告
    announcements_file = read_file('global_announcements', 'json');
    global_announcements = if(announcements_file,decode_json(announcements_file),{});
);

// 服务器关闭时保存数据
__on_close() -> save_global_data();

// 获取玩家消息文件路径
player_data_path(player) -> 'player_msgs/' + player~'uuid' + '.json';

// 获取玩家个人消息
get_player_messages(player) -> (
    path = player_data_path(player);
    f = read_file(path, 'json');
    data = if(f,decode_json(f),null);
    if(data == null ,
        l(null, null, null, null, null, null, null, null, null, null)
    ,
        data
    );
);

// 保存玩家个人消息
save_player_messages(player, messages) -> (
    write_file(player_data_path(player), 'json', encode_json(messages));
);

// 设置个人消息命令处理
set_message(index, message) -> (
    player = player();
    messages = get_player_messages(player);
    messages:index = message;
    save_player_messages(player, messages);
    print(player, format('gi 消息已保存至槽位'+index))
);
clear_message(index) -> (
    player = player();
    messages = get_player_messages(player);
    messages:index = null;
    save_player_messages(player, messages);
    print(player, format('gi 消息已清除对应槽位'+index))
);


// 设置全局公告命令处理
set_announcements(message) -> (
    global_announcements = {'text'-> message, 'time'-> unix_time()};
    save_global_data();
    print(player(), format('gi 全局公告已更新'));
);

clear_announcements() -> (
    global_announcements = {};
    save_global_data();
    print(player(), format('gi 全局公告已清除'));
);

// 捕获玩家聊天消息
__on_player_message(player, message) -> (
    // 添加到全局历史
    entry = {'player'-> player~'name', 'time'-> unix_time(), 'msg'-> message};
    if(length(global_msg_history) >= 30,
        global_msg_history = slice(global_msg_history, 1, 30);
    );
    global_msg_history += entry;
    global_unsaved_changes += 1;
    if(global_unsaved_changes >= 10, save_global_data());
);

time_to_str(time)->(
    date = convert_date(time);
    days = ['周一','周二','周三','周四','周五','周六','周日'];
    str('%s%02d:%02d:%02d', 
        days:(date:6-1), date:3, date:4, date:5 
    )

);

// 玩家登录时显示消息
__on_player_connects(player) -> (
    personal = get_player_messages(player);
    output = [];
    // 添加全局历史
    output += format('rb === 最近消息 ===');
    for(global_msg_history,
        output += format('y <'+_:'player'+'@'+time_to_str(_:'time')+'> ', 'g '+_:'msg')
    );
    // 添加全局公告
    if(global_announcements:'text',
        output += format('rb ===   公告   ===');
        output += format('y '+global_announcements:'text');
        output += format('rb === 个人存档 ===');
    );
    // 添加个人消息
    for(personal, if(_ != null,
        output +=  format('gi '+_, '^ '+_i)
    ));
    
    
    // 发送组合内容
    print(player, format('yb === 消息中心 ==='));
    map(output, print(player, _));
);
