//加入队伍的玩家发送的聊天信息会被拦截，仅告知队内玩家
__config() -> {
    'scope' -> 'global'
};

__on_player_message(player, message) -> (
    if(player~'team' && message~'^[!！]',
        run('/teammsg ' + message~'(?<=^[!！]).*');
        return('cancel'),
    //else
        return()
    )
)