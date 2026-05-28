module AlmacenRobotico where

-- ==========================================
-- Tipos de Datos Iniciales (Dados por el enunciado)
-- ==========================================
type Coord = (Int, Int) -- (Fila, Columna)
data Move = U | D | L | R deriving (Show, Eq)
type State = (Coord, Coord, [Coord]) -- (Robot, CajaObjetivo, CajasDeBloqueo)

-- ==========================================
-- PARTE 1: Inicialización del Estado
-- ==========================================

-- Función auxiliar para verificar si una coordenada está dentro del tablero 6x6
inBounds :: Coord -> Bool
inBounds (f, c) = f >= 0 && f <= 5 && c >= 0 && c <= 5

-- Verifica si hay elementos duplicados en una lista (solapamiento)
hasDuplicates :: Eq a => [a] -> Bool
hasDuplicates []     = False
hasDuplicates (x:xs) = elem x xs || hasDuplicates xs

initialState :: Coord -> Coord -> [Coord] -> State
initialState r cObj bks
    -- 1. Validar límites de todas las entidades
    | not (inBounds r && inBounds cObj && all inBounds bks) = ((-1,-1), (-1,-1), [])
    -- 2. Validar que no existan solapamientos
    | hasDuplicates (r : cObj : bks)                        = ((-1,-1), (-1,-1), [])
    -- Si todo es correcto, retorna el estado
    | otherwise                                             = (r, cObj, bks)


-- ==========================================
-- PARTE 2: Validación de Movimientos
-- ==========================================

-- Función auxiliar para obtener la siguiente coordenada dada una dirección
nextCoord :: Coord -> Move -> Coord
nextCoord (f, c) U = (f - 1, c)
nextCoord (f, c) D = (f + 1, c)
nextCoord (f, c) L = (f, c - 1)
nextCoord (f, c) R = (f, c + 1)

isValidMove :: State -> Move -> Bool
isValidMove (r, cObj, bks) mv
    -- Condición fundamental: El robot no puede salirse del tablero
    | not (inBounds destRobot) = False
    
    -- CASO 1: La casilla destino está vacía (No hay colisión con ninguna caja)
    | destRobot /= cObj && notElem destRobot bks = True
    
    -- CASO 2: El robot intenta empujar la Caja Objetivo
    | destRobot == cObj = inBounds destCaja && destCaja /= cObj && notElem destCaja bks
    
    -- CASO 3: El robot intenta empujar una Caja de Bloqueo
    | elem destRobot bks = inBounds destCaja && destCaja /= cObj && notElem destCaja bks

    | otherwise = False
    where
        destRobot = nextCoord r mv       -- A dónde iría el robot
        destCaja  = nextCoord destRobot mv -- A dónde iría la caja si es empujada


-- ==========================================
-- PARTE 3: Ejecución de Movimiento
-- ==========================================

applyMove :: State -> Move -> State
applyMove (r, cObj, bks) mv
    -- Si choca con la Caja Objetivo, se desplazan ambos
    | destRobot == cObj  = (destRobot, destCaja, bks)
    
    -- Si choca con una Caja de Bloqueo, se desplaza el robot y modificamos la lista de bloques
    | elem destRobot bks = (destRobot, cObj, actualizarBloques bks destRobot destCaja)
    
    -- Movimiento simple a casilla vacía
    | otherwise          = (destRobot, cObj, bks)
    where
        destRobot = nextCoord r mv
        destCaja  = nextCoord destRobot mv
        
        -- Función auxiliar para reemplazar la posición de la caja movida en la lista
        actualizarBloques [] _ _ = []
        actualizarBloques (b:bs) vieja nueva
            | b == vieja = nueva : bs
            | otherwise  = b : actualizarBloques bs vieja nueva