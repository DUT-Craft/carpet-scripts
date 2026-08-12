global_requester = {};
__config() -> {
   'scope'->'player',
	'commands' -> {
		'<player>' -> _(to) -> signal_event('tp_request', to, player()),
      'accept <player>' -> _(src) -> if(has(global_requester,src), 
         //print(player(),'tp '+global_requester:src ~'command_name'+' '+player()~'command_name');
         run('tp '+global_requester:src ~'command_name'+' '+player()~'command_name'); 
         delete(global_requester,src);
      )
	},
   'arguments' -> {
      'player' -> {'type' -> 'players', 'single' -> true}
   }
};

handle_event('tp_request', _(req) -> (
   put(global_requester,req~'name',req);
   print(player(), format(
      'w '+req+' 想传送到你',
      'yb [同意]', '^yb 点击', '!/tpa accept '+req~'name'
   ));
));

__on_player_connects(player)->(
    print(player,format('l 使用/tpa来传送玩家'));
);
