-- =============================================================================
-- UNIVERSIDAD CENTRAL DE VENEZUELA
-- FACULTAD DE CIENCIAS - ESCUELA DE COMPUTACIÓN
--
-- PROYECTO 1: SIMULACIÓN DE ALMACÉN ROBÓTICO (SOKOBAN)
--
-- INTEGRANTES:
--   - Franyer Pérez - C.I.: 30136615
--   - Jorge Arias - C.I.: 30245916
--
-- =============================================================================
module Prurba where
import Data.List (nub)

-- ==========================================
-- Definiciones de Tipos de Datos
-- ==========================================

type Coord = (Int, Int) -- (Fila, Columna)

data Move = U | D | L | R 
    deriving (Show, Eq)

type State = (Coord, Coord, [Coord]) -- (Robot, CajaObjetivo, CajasDeBloqueo)

-- =============================================================================
-- PARTE 1 - INICIALIZACIÓN Y VALIDACIÓN DEL ESTADO
-- =============================================================================

-- Esta función es nuestro Filtro de seguridad. Recibe una coordenada (Fila, Columna)
-- y nos dice si cae dentro de la matriz de 6x6. Como los índices empiezan en 0,
-- el rango válido estricto para que no se salga de los bordes es de 0 a 5.
isValidCoord :: Coord -> Bool
isValidCoord (f, c) = f >= 0 && f <= 5 && c >= 0 && c <= 5


-- Aquí creamos el estado inicial de la simulación. Recibe las posiciones del 
-- robot, de la Caja Objetivo y la lista con las Cajas de Bloqueo ordinarias.
initialState :: Coord -> Coord -> [Coord] -> State
initialState robot goal blocks
    -- Si el robot apareció fuera del mapa (menos de 0 o más de 5), mandamos error.
    | not (isValidCoord robot) = estadoError
    
    -- Si la meta (la caja objetivo) está fuera de los límites, mandamos error.
    | not (isValidCoord goal) = estadoError
    
    -- Aquí usamos 'any' y evaluamos si "al menos una" caja de la lista 
    -- está fuera del mapa. Si hay aunque sea una, todo el estado es inválido.
    | any (not . isValidCoord) blocks = estadoError
    
    -- Si pasaron la prueba de los bordes, verificamos que no estén 
    -- intentando pisarse las mangueras entre ellos (que no compartan la misma casilla).
    | tieneSolapamiento = estadoError
    
    -- Si superaron con éxito todos los filtros anteriores.
    -- Retornamos la tupla ordenada con la estructura original que nos pide el enunciado.
    | otherwise = (robot, goal, blocks)
  where
    -- Guardamos aquí la tupla de error estándar que exige nuestro proyecto : ((-1,-1), (-1,-1), [])
    -- Así no tenemos que escribirla repetidas veces arriba.
    estadoError = ((-1,-1), (-1,-1), [])
    
    -- Para revisar el solapamiento, metemos absolutamente todos los objetos en una lista unificada de coordenadas.
    -- El operador ':' sirve para pegar elementos al inicio de la lista de bloques.
    todasLasCoordenadas  = robot : goal : blocks
    
    -- la función 'nub' toma una lista y elimina los duplicados, si metimos 4 coordenadas en 'allCoords' y nadie se solapaba, 'nub' la deja idéntica.
    -- Pero si dos cosas compartían posición, 'nub' borrará una. Al comparar las longitudes con '/=' (diferente de), si el tamaño cambió, sabemos que hubo solapamiento.
    tieneSolapamiento = length todasLasCoordenadas /= length (nub todasLasCoordenadas)

-- =============================================================================
-- PARTE 2 - VALIDACIÓN DE MOVIMIENTOS
-- =============================================================================

-- Función auxiliar para calcular a dónde se movería una coordenada.
-- Recibe un punto (Fila, Columna) y una dirección, devolviendo el nuevo punto.
moveCoord :: Coord -> Move -> Coord
moveCoord (f, c) U = (f - 1, c)     -- Subir resta una fila.
moveCoord (f, c) D = (f + 1, c)     -- Bajar suma una fila.
moveCoord (f, c) L = (f, c - 1)     -- Ir a la izquierda resta una columna.
moveCoord (f, c) R = (f, c + 1)     -- Ir a la derecha suma una columna.


-- Determina si un movimiento es legal considerando colisiones y límites del mapa.
isValidMove :: State -> Move -> Bool
isValidMove (robot, goal, blocks) dir
    -- Control de fronteras para el Robot.
    -- Si el paso directo del robot lo saca de la matriz 6x6, el movimiento muere aquí.
    | not (isValidCoord siguienteRobot) = False
    
    -- Control de colisión con Cajas (Mecánica de empuje).
    -- Evaluamos si la casilla a la que va el robot coincide con la caja objetivo
    -- o con cualquier caja de la lista de bloqueo (usando 'elem').
    | siguienteRobot == goal || siguienteRobot `elem` blocks =
        -- Si hay una caja, el movimiento solo será válido si la casilla que queda
        -- justo detrás de ella está dentro del mapa Y está completamente limpia.
        isValidCoord detrasDeCaja && detrasDeCaja /= goal && not (detrasDeCaja `elem` blocks)
        
    -- Camino despejado.
    -- Si la casilla está dentro del mapa y no colisionó con ninguna caja en el paso anterior,
    -- significa que el robot se está moviendo a una casilla vacía legal.
    | otherwise = True
  where
    -- Calculamos de antemano las posiciones clave usando nuestra función auxiliar:
    siguienteRobot = moveCoord robot dir -- La casilla a la que el robot quiere avanzar.
    detrasDeCaja = moveCoord siguienteRobot dir -- La casilla a donde se desplazaría la caja empujada. 

-- =============================================================================
-- PARTE 3 - EJECUCIÓN DE MOVIMIENTO
-- =============================================================================

-- Esta función toma el estado actual del almacén y un movimiento, generando
-- un nuevo estado con las posiciones actualizadas de todos los elementos.
applyMove :: State -> Move -> State
applyMove (robot, goal, blocks) dir = (siguienteRobot, siguienteGoal, siguientesBlocks)
  where
    -- El robot se desplaza obligatoriamente a su siguiente casilla calculada.
    siguienteRobot = moveCoord robot dir
    
    -- Evaluación del empuje de la Caja Objetivo. Si la casilla a la que avanza el robot coincide exactamente con la posición
    -- actual de la caja objetivo, significa que la está empujando. Por ende, la caja se rueda una casilla extra en esa misma dirección. Si no, se queda quieta.
    siguienteGoal = if siguienteRobot == goal 
                    then moveCoord goal dir 
                    else goal
                   
    -- Evaluación de la lista de Cajas de Bloqueo. Usamos 'map' para recorrer la lista de bloques y transformar sus coordenadas.
    -- Para cada caja 'b' de la lista, hacemos una pregunta individual: ¿El robot va a pisar esta caja en específico?
    -- Si la respuesta es sí, recalculamos su posición rodándola hacia adelante;
    -- si es no, dejamos la caja intacta exactamente donde estaba en el mapa.
    siguientesBlocks = map (\b -> if siguienteRobot == b then moveCoord b dir else b) blocks 

-- =============================================================================
-- PARTE 4 - MEJOR SOLUCIÓN 
-- =============================================================================

-- Determina si un estado es ganador. El juego termina con éxito si
-- la Caja Objetivo llega a la esquina (5,5).
isGoalState :: State -> Bool
isGoalState (_, (5,5), _) = True
isGoalState _ = False

-- Función principal solicitada en el enunciado.
-- Retorna una tupla: (Número total de movimientos, Lista con la secuencia de estados).
solveWarehouse :: State -> (Int, [State])
solveWarehouse estadoInicial
    -- Si el estado inicial ya está en la meta, terminamos con 0 movimientos.
    | isGoalState estadoInicial = (0, [estadoInicial])
    -- De lo contrario, arrancamos el BFS.
    -- Inicializamos la cola con un camino que contiene solo el estado inicial: [[initialState]]
    -- Inicializamos los visitados conteniendo solo el estado inicial: [initialState]
    | otherwise = bfs [[estadoInicial]] [estadoInicial]

-- Función auxiliar que ejecuta la recursión del BFS.
-- Parámetros:
-- 1. La cola de exploración: una lista de caminos (donde cada camino es [State], ordenado del último al primero).
-- 2. La lista de estados que ya han sido visitados para evitar ciclos.
bfs :: [[State]] -> [State] -> (Int, [State])
bfs [] _ = (0, []) -- Si la cola se vacía, significa que exploramos todo y no hay solución.
bfs (caminoActual:cola) visitados
    -- ¡Victoria! Si el estado más reciente del camino actual es la meta.
    | isGoalState estadoActual = (length caminoActual - 1, reverse caminoActual)
    -- Si no es meta, expandimos el nodo generando sus vecinos válidos no visitados.
    | otherwise = bfs (cola ++ nuevosCaminos) (visitados ++ nuevosEstados)
  where
    -- El estado actual es el que está al frente del camino (el último al que llegamos).
    estadoActual = head caminoActual

    -- Generamos los movimientos posibles de forma legal.
    -- Tomamos las 4 direcciones básicas, filtramos cuáles son válidas desde aquí
    -- y aplicamos el movimiento para obtener los estados resultantes.
    todosLosMovimientos = [U, D, L, R]
    vecinosValidos = [ applyMove estadoActual m | m <- todosLosMovimientos, isValidMove estadoActual m ]

    -- Filtramos para quedarnos unicamente con los estados que nunca hemos visitado.
    vecinosNoVisitados = [ n | n <- vecinosValidos, not (n `elem` visitados) ]

    -- Construimos los nuevos caminos extendidos para la cola.
    -- Para cada vecino nuevo, creamos un nuevo camino metiéndolo al frente.
    nuevosCaminos = [ n : caminoActual | n <- vecinosNoVisitados ]

    -- Guardamos los nuevos estados en el registro de visitados.
    nuevosEstados = vecinosNoVisitados