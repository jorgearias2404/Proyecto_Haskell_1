% =============================================================================
% UNIVERSIDAD CENTRAL DE VENEZUELA
% FACULTAD DE CIENCIAS - ESCUELA DE COMPUTACIÓN
% PROYECTO 2: SIMULACIÓN DE ALMACÉN ROBÓTICO 
% =============================================================================

:- dynamic robot/2.
:- dynamic caja_objetivo/2.
:- dynamic caja_bloqueo/2.

% =============================================================================
% PARTE 1 - INICIALIZACIÓN DEL TABLERO
% =============================================================================
isValidCoord(Row, Col) :-
    Row >= 0, Row =< 5,
    Col >= 0, Col =< 5.

% Predicado principal de inicialización
initialBoard((R_Row, R_Col), (T_Row, T_Col), BlockingBoxes) :-
    % 1. Validar límites de la matriz 6x6 para Robot y Caja Objetivo
    isValidCoord(R_Row, R_Col),
    isValidCoord(T_Row, T_Col),
    % 2. Validar límites de todas las cajas de bloqueo
    forall(member((B_Row, B_Col), BlockingBoxes), isValidCoord(B_Row, B_Col)),
    % 3. Validar solapamientos (recolectamos todas las entidades y verificamos duplicados)
    AllCoords = [(R_Row, R_Col), (T_Row, T_Col) | BlockingBoxes],
    is_set(AllCoords), !,
    
    % 4. Si todo es válido, limpiar la base de conocimientos antes de cargar
    retractall(robot(_, _)),
    retractall(caja_objetivo(_, _)),
    retractall(caja_bloqueo(_, _)),
    
    % 5. Cargar nuevos hechos
    assertz(robot(R_Row, R_Col)),
    assertz(caja_objetivo(T_Row, T_Col)),
    forall(member((Br, Bc), BlockingBoxes), assertz(caja_bloqueo(Br, Bc))).

% =============================================================================
% PARTE 2 - VALIDACIÓN DE MOVIMIENTOS
% =============================================================================

% Auxiliar para calcular el desplazamiento según la dirección dada
move_coord(Row, Col, 'u', NewRow, Col) :- NewRow is Row - 1.
move_coord(Row, Col, 'd', NewRow, Col) :- NewRow is Row + 1.
move_coord(Row, Col, 'l', Row, NewCol) :- NewCol is Col - 1.
move_coord(Row, Col, 'r', Row, NewCol) :- NewCol is Col + 1.

% Predicado isValidMove(+CurrentState, +Move)
isValidMove(state((R_Row, R_Col), (T_Row, T_Col), Blocks), Move) :-
    % Determinar a dónde planea moverse el Robot
    move_coord(R_Row, R_Col, Move, NextR_Row, NextR_Col),
    isValidCoord(NextR_Row, NextR_Col),
    
    % Escenario A: La siguiente casilla contiene la Caja Objetivo
    (   NextR_Row == T_Row, NextR_Col == T_Col
    ->  move_coord(T_Row, T_Col, Move, Behind_Row, Behind_Col),
        isValidCoord(Behind_Row, Behind_Col),
        \+ member((Behind_Row, Behind_Col), Blocks)
        
    % Escenario B: La siguiente casilla contiene una Caja de Bloqueo
    ;   member((NextR_Row, NextR_Col), Blocks)
    ->  move_coord(NextR_Row, NextR_Col, Move, Behind_Row, Behind_Col),
        isValidCoord(Behind_Row, Behind_Col),
        \+ (Behind_Row == T_Row, Behind_Col == T_Col),
        \+ member((Behind_Row, Behind_Col), Blocks)
        
    % Escenario C: La casilla está completamente vacía
    ;   true
    ).

% =============================================================================
% PARTE 3 - EJECUCIÓN DE MOVIMIENTO
% =============================================================================

% Predicado moveRobot(+CurrentState, +Move, -NewState)
moveRobot(CurrentState, Move, _) :-
    \+ isValidMove(CurrentState, Move), !, fail.
    
moveRobot(state((R_Row, R_Col), (T_Row, T_Col), Blocks), Move, state((NextR_Row, NextR_Col), (NewT_Row, NewT_Col), NewBlocks)) :-
    % Calculamos la nueva posición del robot
    move_coord(R_Row, R_Col, Move, NextR_Row, NextR_Col),
    
    % Actualizar posición de la Caja Objetivo si es empujada
    (   NextR_Row == T_Row, NextR_Col == T_Col
    ->  move_coord(T_Row, T_Col, Move, NewT_Row, NewT_Col)
    ;   NewT_Row = T_Row, NewT_Col = T_Col
    ),
    
    % Actualizar la lista de Cajas de Bloqueo si alguna fue empujada
    maplist(update_block(NextR_Row, NextR_Col, Move), Blocks, NewBlocks).

% Auxiliar para mapear y desplazar el bloque colisionado individualmente
update_block(NextR_Row, NextR_Col, Move, (B_Row, B_Col), (NewB_Row, NewB_Col)) :-
    (   NextR_Row == B_Row, NextR_Col == B_Col
    ->  move_coord(B_Row, B_Col, Move, NewB_Row, NewB_Col)
    ;   NewB_Row = B_Row, NewB_Col = B_Col
    ).

% =============================================================================
% PARTE 4 - SOLUCIÓN (ALGORITMO BFS)
% =============================================================================

% Condición de victoria: la Caja Objetivo alcanza la esquina (5,5)
isGoalState(state(_, (5, 5), _)).

% Predicado principal: solveWarehouse(+StartState, -Solution)
solveWarehouse(StartState, Solution) :-
    % Inicializamos la cola con un camino vacío y el estado inicial: [path(state, [])]
    % El conjunto de visitados arranca con el estado inicial: [state]
    bfs_queue([path(StartState, [])], [StartState], Solution).

% BFS Auxiliar manejando la cola de caminos
% Caso Base: Si el camino al frente de la cola ya llegó a la meta, esa es nuestra solución mínima
bfs_queue([path(CurrentState, Actions) | _], _, Solution) :-
    isGoalState(CurrentState), !,
    reverse(Actions, Solution).

% Caso Recursivo: Expandir el nodo al frente y encolar sus vecinos válidos no visitados
bfs_queue([path(CurrentState, Actions) | RestQueue], Visited, Solution) :-
    % Encontrar todos los caminos sucesores válidos de un paso
    findall(
        path(NextState, [Move | Actions]),
        (   member(Move, ['u', 'd', 'l', 'r']),
            isValidMove(CurrentState, Move),
            moveRobot(CurrentState, Move, NextState),
            \+ member(NextState, Visited)
        ),
        NewPaths
    ),
    
    % Extraer los nuevos estados para agregarlos al registro de visitados
    extract_states(NewPaths, NewStates),
    append(Visited, NewStates, UpdatedVisited),
    
    % Colocar los nuevos caminos al final de la cola (Garantía de BFS para mínimo de pasos)
    append(RestQueue, NewPaths, NewQueue),
    
    % Continuar la búsqueda
    bfs_queue(NewQueue, UpdatedVisited, Solution).

% Auxiliar para recolectar estados de una lista de estructuras path
extract_states([], []).
extract_states([path(State, _) | Rest], [State | RestStates]) :-
    extract_states(Rest, RestStates).