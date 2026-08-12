// Tetris2D for Carpet Scarpet
//
// README
// - Load this app with: /script load tetris2d
// - Configure the playfield with:
//   /tetris2d setup <origin_a> <origin_b> <height> [drop_ticks] [fast_multiplier]
// - Start a new game with: /tetris2d start [controller_player]
// - Stop the active piece with: /tetris2d stop
// - Reset the board with: /tetris2d reset
// - Controls use hotbar slot switching and drop events from the controller player:
//   - slot 2 / 3 = move left / right
//   - slot 1 / 4 = rotate counter-clockwise / clockwise
//   - press Q while on one of those slots to repeat the same action without changing the slot
//   - hold sneak = fast drop (default 3x)
// - The score is shown in the sidebar scoreboard while the game is running.
//
// The active tetromino is rendered with scarpet markers and block models so it can move smoothly.
// When a piece lands, it is converted into normal solid blocks and the board state is updated.

__config() -> {
	'scope' -> 'global',
	'command_permission' -> 'ops',
	'commands' -> {
		'' -> 'tetris2dHelp',
		'help' -> 'tetris2dHelp',
		'setup <origin_a> <origin_b> <height>' -> 'tetris2dSetup',
		'setup <origin_a> <origin_b> <height> <drop_ticks> <fast_multiplier>' -> 'tetris2dSetupAdvanced',
		'start' -> 'tetris2dStartDefault',
		'start <controller_player>' -> 'tetris2dStartWithController',
		'stop' -> 'tetris2dStop',
		'reset' -> 'tetris2dReset'
	},
	'arguments' -> {
		'origin_a' -> {'type' -> 'pos', 'loaded' -> true},
		'origin_b' -> {'type' -> 'pos', 'loaded' -> true},
		'height' -> {'type' -> 'int', 'min' -> 4, 'suggest' -> [20]},
		'drop_ticks' -> {'type' -> 'int', 'min' -> 1, 'suggest' -> [20]},
		'fast_multiplier' -> {'type' -> 'int', 'min' -> 1, 'suggest' -> [3]},
		'controller_player' -> {'type' -> 'players', 'single' -> true}
	}
};

global_tetris2d_origin = null;
global_tetris2d_axis = null;
global_tetris2d_step = 1;
global_tetris2d_width = null;
global_tetris2d_height = null;
global_tetris2d_drop_ticks = 20;
global_tetris2d_fast_multiplier = 3;
global_tetris2d_controller = null;
global_tetris2d_running = false;
global_tetris2d_game_over = false;
global_tetris2d_accumulator = 0;
global_tetris2d_board = {};
global_tetris2d_bag = [];
global_tetris2d_piece_name = null;
global_tetris2d_piece_cells = [];
global_tetris2d_piece_origin = [0, 0];
global_tetris2d_piece_order = ['I', 'O', 'T', 'S', 'Z', 'J', 'L'];
global_tetris2d_score_objective = 'tetris2d_score';
global_tetris2d_shapes = {
	'I' -> [[0, 1], [1, 1], [2, 1], [3, 1]],
	'O' -> [[1, 1], [2, 1], [1, 2], [2, 2]],
	'T' -> [[0, 1], [1, 1], [2, 1], [1, 2]],
	'S' -> [[1, 0], [2, 0], [0, 1], [1, 1]],
	'Z' -> [[0, 0], [1, 0], [1, 1], [2, 1]],
	'J' -> [[0, 0], [0, 1], [1, 1], [2, 1]],
	'L' -> [[2, 0], [0, 1], [1, 1], [2, 1]]
};
global_tetris2d_colors = {
	'I' -> 'cyan_concrete',
	'O' -> 'yellow_concrete',
	'T' -> 'purple_concrete',
	'S' -> 'lime_concrete',
	'Z' -> 'red_concrete',
	'J' -> 'blue_concrete',
	'L' -> 'orange_concrete'
};

tetris2d_abs(n) -> if (n < 0, -n, n);

tetris2d_sign(n) -> if (n < 0, -1, 1);

tetris2d_key(x, y) -> str(x) + '|' + str(y);

tetris2d_controller_entity() -> if (global_tetris2d_controller, player(global_tetris2d_controller), null);

tetris2d_notice(message) -> (
	controller = tetris2d_controller_entity();
	if (controller, print(controller, message), print(message))
);

tetris2d_scoreboard_key() -> if (global_tetris2d_controller, global_tetris2d_controller, 'Tetris2D');

tetris2d_scoreboard_ensure() -> (
	if (!scoreboard(global_tetris2d_score_objective),
		scoreboard_add(global_tetris2d_score_objective)
	);
	scoreboard_property(global_tetris2d_score_objective, 'display_name', format('b Tetris2D'))
);

tetris2d_scoreboard_show() -> (
	tetris2d_scoreboard_ensure();
	scoreboard_display('sidebar', global_tetris2d_score_objective)
);

tetris2d_scoreboard_hide() -> scoreboard_display('sidebar', null);

tetris2d_scoreboard_reset() -> (
	tetris2d_scoreboard_ensure();
	scoreboard(global_tetris2d_score_objective, tetris2d_scoreboard_key(), 0)
);

tetris2d_scoreboard_add(points) -> (
	tetris2d_scoreboard_ensure();
	current_score = scoreboard(global_tetris2d_score_objective, tetris2d_scoreboard_key());
	if (!current_score, current_score = 0);
	scoreboard(global_tetris2d_score_objective, tetris2d_scoreboard_key(), current_score + points)
);

tetris2d_world_pos(local_x, local_y) -> (
	if (global_tetris2d_axis == 'x',
		[global_tetris2d_origin:0 + local_x * global_tetris2d_step, global_tetris2d_origin:1 + local_y, global_tetris2d_origin:2],
		[global_tetris2d_origin:0, global_tetris2d_origin:1 + local_y, global_tetris2d_origin:2 + local_x * global_tetris2d_step]
	)
);

tetris2d_piece_box(cells) -> (
	min_x = 999;
	min_y = 999;
	max_x = -999;
	max_y = -999;
	for (cells,
		cell_x = get(_, 0);
		cell_y = get(_, 1);
		if (cell_x < min_x, min_x = cell_x);
		if (cell_y < min_y, min_y = cell_y);
		if (cell_x > max_x, max_x = cell_x);
		if (cell_y > max_y, max_y = cell_y)
	);
	[min_x, min_y, max_x, max_y]
);

tetris2d_normalize_cells(cells) -> (
	box = tetris2d_piece_box(cells);
	min_x = box:0;
	min_y = box:1;
	map(cells, [get(_, 0) - min_x, get(_, 1) - min_y])
);

tetris2d_rotate_cells(cells, turns) -> (
	rotated = cells;
	loop(turns,
		rotated = map(rotated, [3 - get(_, 1), get(_, 0)])
	);
	tetris2d_normalize_cells(rotated)
);

tetris2d_clear_playfield() -> (
	c_for(y = 0, y < global_tetris2d_height, y += 1,
		c_for(x = 0, x < global_tetris2d_width, x += 1,
			set(tetris2d_world_pos(x, y), 'air')
		)
	)
);

tetris2d_redraw_board() -> (
	c_for(y = 0, y < global_tetris2d_height, y += 1,
		c_for(x = 0, x < global_tetris2d_width, x += 1,
			key = tetris2d_key(x, y);
			if (has(global_tetris2d_board, key),
				set(tetris2d_world_pos(x, y), get(global_tetris2d_colors, get(global_tetris2d_board, key))),
				set(tetris2d_world_pos(x, y), 'air')
			)
		)
	)
);

tetris2d_remove_active_piece() -> (
	remove_all_markers();
	for (entity_selector('@e[tag=tetris2d_active]'),
		modify(_, 'kill')
	)
);

tetris2d_render_active_piece() -> (
	tetris2d_remove_active_piece();
	piece_color = get(global_tetris2d_colors, global_tetris2d_piece_name);
	for (global_tetris2d_piece_cells,
		pos = tetris2d_world_pos(global_tetris2d_piece_origin:0 + get(_, 0), global_tetris2d_piece_origin:1 + get(_, 1));
		data = nbt('{}');
		put(data, 'block_state.Name', piece_color);
		put(data, 'Glowing', true);
		put(data, 'transformation', {
			'left_rotation' -> {'angle' -> 0.0, 'axis' -> [1.0, 0.0, 0.0]},
			'right_rotation' -> {'angle' -> 0.0, 'axis' -> [1.0, 0.0, 0.0]},
			'scale' -> [1, 1, 1],
			'translation' -> [0, 0, 0]
		});
		entity = spawn('minecraft:block_display', pos, data);
		modify(entity, 'tag', 'tetris2d_active')
	)
);

tetris2d_can_place(cells, origin_x, origin_y) -> (
	allowed = true;
	for (cells,
		target_x = origin_x + get(_, 0);
		target_y = origin_y + get(_, 1);
		if (target_x < 0 || target_x >= global_tetris2d_width || target_y < 0 || target_y >= global_tetris2d_height,
			allowed = false
		);
		if (allowed && has(global_tetris2d_board, tetris2d_key(target_x, target_y)),
			allowed = false
		)
	);
	allowed
);

tetris2d_try_move(dx, dy) -> (
	next_x = global_tetris2d_piece_origin:0 + dx;
	next_y = global_tetris2d_piece_origin:1 + dy;
	if (!tetris2d_can_place(global_tetris2d_piece_cells, next_x, next_y),
		false,
		(
			global_tetris2d_piece_origin = [next_x, next_y];
			tetris2d_render_active_piece();
			true
		)
	)
);

tetris2d_try_rotate(direction) -> (
	turns = if (direction < 0, 3, 1);
	rotated = tetris2d_rotate_cells(global_tetris2d_piece_cells, turns);
	kicks = [[0, 0], [1, 0], [-1, 0], [2, 0], [-2, 0], [0, 1], [0, -1]];
	success = false;
	for (kicks,
		if (!success,
			kick_x = get(_, 0);
			kick_y = get(_, 1);
			candidate_x = global_tetris2d_piece_origin:0 + kick_x;
			candidate_y = global_tetris2d_piece_origin:1 + kick_y;
			if (tetris2d_can_place(rotated, candidate_x, candidate_y),
				global_tetris2d_piece_cells = rotated;
				global_tetris2d_piece_origin = [candidate_x, candidate_y];
				tetris2d_render_active_piece();
				success = true
			)
		)
	);
	success
);

tetris2d_make_bag() -> global_tetris2d_bag = sort_key(global_tetris2d_piece_order, rand(1));

tetris2d_next_piece_name() -> (
	if (!length(global_tetris2d_bag), tetris2d_make_bag());
	piece_name = global_tetris2d_bag:0;
	delete(global_tetris2d_bag, 0);
	piece_name
);

tetris2d_clear_lines() -> (
	new_board = {};
	cleared = 0;
	c_for(y = 0, y < global_tetris2d_height, y += 1,
		full = true;
		c_for(x = 0, x < global_tetris2d_width, x += 1,
			if (!has(global_tetris2d_board, tetris2d_key(x, y)), full = false)
		);
		if (full,
			cleared += 1,
			c_for(x = 0, x < global_tetris2d_width, x += 1,
				key = tetris2d_key(x, y);
				if (has(global_tetris2d_board, key),
					put(new_board, tetris2d_key(x, y - cleared), get(global_tetris2d_board, key))
				)
			)
		)
	);
	global_tetris2d_board = new_board;
	if (cleared,
		tetris2d_scoreboard_add(cleared)
	);
	tetris2d_redraw_board();
	cleared
);

tetris2d_lock_piece() -> (
	for (global_tetris2d_piece_cells,
		board_x = global_tetris2d_piece_origin:0 + get(_, 0);
		board_y = global_tetris2d_piece_origin:1 + get(_, 1);
		put(global_tetris2d_board, tetris2d_key(board_x, board_y), global_tetris2d_piece_name)
	);
	tetris2d_remove_active_piece();
	tetris2d_clear_lines();
	tetris2d_redraw_board();
	tetris2d_spawn_piece()
);

tetris2d_spawn_piece() -> (
	piece_name = tetris2d_next_piece_name();
	piece_cells = tetris2d_normalize_cells(get(global_tetris2d_shapes, piece_name));
	box = tetris2d_piece_box(piece_cells);
	piece_width = box:2 - box:0 + 1;
	piece_height = box:3 - box:1 + 1;
	spawn_x = floor((global_tetris2d_width - piece_width) / 2);
	spawn_y = global_tetris2d_height - piece_height;
	if (!tetris2d_can_place(piece_cells, spawn_x, spawn_y),
		(
			global_tetris2d_running = false;
			global_tetris2d_game_over = true;
			global_tetris2d_piece_name = null;
			global_tetris2d_piece_cells = [];
			global_tetris2d_piece_origin = [spawn_x, spawn_y];
			tetris2d_remove_active_piece();
			tetris2d_notice('Tetris2D game over');
			false
		),
		(
			global_tetris2d_piece_name = piece_name;
			global_tetris2d_piece_cells = piece_cells;
			global_tetris2d_piece_origin = [spawn_x, spawn_y];
			tetris2d_render_active_piece();
			true
		)
	)
);

tetris2d_reset() -> (
	global_tetris2d_running = false;
	global_tetris2d_game_over = false;
	global_tetris2d_accumulator = 0;
	global_tetris2d_board = {};
	global_tetris2d_piece_name = null;
	global_tetris2d_piece_cells = [];
	global_tetris2d_piece_origin = [0, 0];
	global_tetris2d_bag = [];
	tetris2d_remove_active_piece();
	tetris2d_scoreboard_hide();
	if (global_tetris2d_width && global_tetris2d_height, tetris2d_clear_playfield())
);

tetris2d_setup(origin_a, origin_b, height, ...extra) -> (
	[x1, y1, z1] = origin_a;
	[x2, y2, z2] = origin_b;
	dx = x2 - x1;
	dz = z2 - z1;
	if (tetris2d_abs(dx) >= tetris2d_abs(dz),
		(
			global_tetris2d_axis = 'x';
			global_tetris2d_step = tetris2d_sign(dx);
			global_tetris2d_width = tetris2d_abs(dx) + 1;
		),
		(
			global_tetris2d_axis = 'z';
			global_tetris2d_step = tetris2d_sign(dz);
			global_tetris2d_width = tetris2d_abs(dz) + 1;
		)
	);
	global_tetris2d_origin = [x1, y1, z1];
	global_tetris2d_height = height;
	global_tetris2d_drop_ticks = if (length(extra) > 0, extra:0, 20);
	global_tetris2d_fast_multiplier = if (length(extra) > 1, extra:1, 3);
	if (global_tetris2d_width < 4 || global_tetris2d_height < 4,
		(
			tetris2d_notice('Tetris2D setup failed: width and height must both be at least 4');
			false
		),
		(
			tetris2d_reset();
			tetris2d_notice('Tetris2D configured: ' + global_tetris2d_width + 'x' + global_tetris2d_height + ', drop=' + global_tetris2d_drop_ticks + ', fast=' + global_tetris2d_fast_multiplier);
			true
		)
	)
);


tetris2dSetup(origin_a, origin_b, height) -> tetris2d_setup(origin_a, origin_b, height);

tetris2dSetupAdvanced(origin_a, origin_b, height, drop_ticks, fast_multiplier) -> tetris2d_setup(origin_a, origin_b, height, drop_ticks, fast_multiplier);

tetris2dStartDefault() -> tetris2d_start();

tetris2dStartWithController(controller_player) -> tetris2d_start(controller_player);

tetris2dStop() -> tetris2d_stop();

tetris2dReset() -> tetris2d_reset();

tetris2dHelp() -> (
	print(player(), format('c Tetris2D 指令：'));
	print(player(), format('d /tetris2d ', 'e setup ', 'w <origin_a> <origin_b> <height> [drop_ticks] [fast_multiplier]'));
	print(player(), format('d /tetris2d ', 'e start ', 'w [controller_player]'));
	print(player(), format('d /tetris2d ', 'e stop'));
	print(player(), format('d /tetris2d ', 'e reset'));
	print(player(), format('y 说明：原点是场地左下角，第二原点是右下角，脚本会自动判断场地沿 X 轴还是 Z 轴展开。'));
	print(player(), format('y 控制：切换到对应热键槽位后，按 Q 可以重复当前动作。'))
);

tetris2d_handle_control_slot(slot) -> (
	if (!global_tetris2d_running || !global_tetris2d_controller, false,
		if (slot == 1, tetris2d_try_move(-1, 0),
		if (slot == 2, tetris2d_try_move(1, 0),
		if (slot == 0, tetris2d_try_rotate(-1),
		if (slot == 3, tetris2d_try_rotate(1), false)))))
);

tetris2d_start(...controller) -> (
	if (!global_tetris2d_width || !global_tetris2d_height,
		(
			tetris2d_notice('Tetris2D is not configured yet. Run tetris2d_setup first.');
			false
		),
		(
			if (length(controller),
				global_tetris2d_controller = controller:0,
				global_tetris2d_controller = if (player(), player() ~ 'name', null)
			);
			tetris2d_reset();
			global_tetris2d_running = true;
			global_tetris2d_game_over = false;
			global_tetris2d_accumulator = 0;
			tetris2d_scoreboard_reset();
			tetris2d_scoreboard_show();
			tetris2d_make_bag();
			if (tetris2d_spawn_piece(),
				tetris2d_notice('Tetris2D started'),
				false
			)
		)
	)
);

tetris2d_stop() -> (
	global_tetris2d_running = false;
	global_tetris2d_accumulator = 0;
	global_tetris2d_piece_name = null;
	global_tetris2d_piece_cells = [];
	tetris2d_remove_active_piece();
	tetris2d_notice('Tetris2D stopped')
);

tetris2d_tick() -> (
	if (!global_tetris2d_running, false,
		(
			controller = tetris2d_controller_entity();
			speed = if (controller && controller ~ 'sneaking', global_tetris2d_fast_multiplier, 1);
			global_tetris2d_accumulator = global_tetris2d_accumulator + speed;
			while (global_tetris2d_accumulator >= global_tetris2d_drop_ticks && global_tetris2d_running,
				global_tetris2d_accumulator = global_tetris2d_accumulator - global_tetris2d_drop_ticks;
				if (!tetris2d_try_move(0, -1),
					(
						tetris2d_lock_piece();
						break()
					)
				)
			);
			true
		)
	)
);

__on_tick() -> tetris2d_tick();

__on_player_switches_slot(player, from, to) -> (
	if (global_tetris2d_controller && player ~ 'name' == global_tetris2d_controller,
		tetris2d_handle_control_slot(to)
	)
);

__on_player_drops_item(player) -> (
	if (global_tetris2d_controller && player ~ 'name' == global_tetris2d_controller,
		selected_slot = query(player, 'selected_slot');
		if (selected_slot == 0 || selected_slot == 1 || selected_slot == 2 || selected_slot == 3,
			(
				tetris2d_handle_control_slot(selected_slot);
				'cancel'
			)
		)
	)
);

__on_player_drops_stack(player) -> __on_player_drops_item(player);

__on_close() -> tetris2d_remove_active_piece();
