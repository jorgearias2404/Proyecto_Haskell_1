module Main where

import Proyecto1 (Coord, Move(..), State, initialState, isValidMove, applyMove, solveWarehouse)
-- Debe exportar: initialState, isValidMove, applyMove, solveWarehouse
-- Tipos: Coord, Move(U, D, L, R), State

-- ==========================================
-- 1. UTILIDADES GENERALES
-- ==========================================

runTest :: (Show a, Eq a) => String -> Float -> a -> a -> IO Float
runTest name pointsVal actual expected = do
    putStr $ "Test [" ++ name ++ "]: "
    if actual == expected
        then do
            putStrLn $ "PASÓ (+ " ++ formatScore pointsVal ++ " pts) ✅"
            return pointsVal
        else do
            putStrLn "FALLÓ ❌"
            putStrLn "   -------------------------------------------------------"
            putStrLn $ "   ❌ OBTENIDO: " ++ show actual
            putStrLn $ "   ✅ ESPERADO: " ++ show expected
            putStrLn "   -------------------------------------------------------"
            return 0.0

formatScore :: Float -> String
formatScore value = show (fromIntegral (round (value * 10)) / 10 :: Float)

-- ==========================================
-- 2. UTILIDADES ESPECÍFICAS PARA PARTE 4
-- ==========================================

isWinningState :: State -> Bool
isWinningState (_, targetCoord, _) = targetCoord == (5,5)

isLegalTransition :: State -> State -> Bool
isLegalTransition fromState toState =
    or [isValidMove fromState mv && applyMove fromState mv == toState | mv <- [U, D, L, R]]

isLegalPath :: State -> [State] -> Bool
isLegalPath _ [] = False
isLegalPath startState path@(firstState:restStates) =
    firstState == startState &&
    and (zipWith isLegalTransition path restStates)

validateWarehouse :: String -> Float -> State -> (Int, [State]) -> Int -> IO Float
validateWarehouse name pointsVal initialSt (actualSteps, actualPath) expectedSteps = do
    putStr $ "Test [" ++ name ++ "]: "
    
    let pathLength = length actualPath
    let lastState = if null actualPath then ((-1,-1), (-1,-1), []) else last actualPath
    let solvedCorrectly = if null actualPath then False else isWinningState lastState
    let stepsMatch = actualSteps == expectedSteps
    let consistency = pathLength == (actualSteps + 1)
    let startsCorrectly = case actualPath of
            [] -> False
            firstState:_ -> firstState == initialSt
    let legalPath = isLegalPath initialSt actualPath

    -- Para casos sin solución (expectedSteps == 0 y camino vacío)
    let isNoSolutionCase = expectedSteps == 0
    let correctlyIdentifiedNoSolution = isNoSolutionCase && actualSteps == 0 && null actualPath

    if (stepsMatch && solvedCorrectly && consistency && startsCorrectly && legalPath) || correctlyIdentifiedNoSolution
        then do
            putStrLn $ "PASÓ (+ " ++ formatScore pointsVal ++ " pts) ✅"
            return pointsVal
        else do
            putStrLn "FALLÓ ❌"
            putStrLn "   -------------------------------------------------------"
            if not stepsMatch && not isNoSolutionCase
                then putStrLn $ "   ⚠️  ERROR DE PASOS: Esperados " ++ show expectedSteps ++ ", Obtenidos " ++ show actualSteps
                else return ()
            
            if not solvedCorrectly && not isNoSolutionCase
                then putStrLn "   ⚠️  ERROR DE SOLUCIÓN: La Caja Objetivo no llegó a (5,5) en el estado final."
                else return ()

            if not consistency && not isNoSolutionCase && not (null actualPath)
                then putStrLn $ "   ⚠️  INCONSISTENCIA: Reportas " ++ show actualSteps ++ " pasos, pero tu lista de estados tiene " ++ show pathLength ++ " elementos."
                else return ()

            if not startsCorrectly && not isNoSolutionCase && not (null actualPath)
                then putStrLn "   ⚠️  ERROR DE CAMINO: La secuencia no comienza en el estado inicial evaluado."
                else return ()

            if not legalPath && not isNoSolutionCase && not (null actualPath)
                then putStrLn "   ⚠️  ERROR DE CAMINO: Hay al menos una transición que no corresponde a un movimiento legal."
                else return ()

            putStrLn "   -------------------------------------------------------"
            return 0.0

-- ==========================================
-- 3. MAIN
-- ==========================================

main :: IO ()
main = do
    putStrLn "================================================"
    putStrLn "   SCRIPT DE EVALUACIÓN FINAL: SOKOBAN SIMPLIFICADO"
    putStrLn "================================================"

    -- PARTE 1
    putStrLn "\n--- PARTE 1: Inicialización (1.0 pto) ---"
    s1_0 <- runTest "P1-Coordenada Negativa" 0.15 (initialState (-1,0) (2,2) []) ((-1,-1), (-1,-1), [])
    s1_1 <- runTest "P1-Normal" 0.2 (initialState (0,0) (2,2) [(1,1), (3,3)]) ((0,0), (2,2), [(1,1), (3,3)])
    s1_2 <- runTest "P1-Solapamiento Robot-Target" 0.15 (initialState (1,1) (1,1) [(3,3)]) ((-1,-1), (-1,-1), [])
    s1_3 <- runTest "P1-Solapamiento Target-Bloqueo" 0.15 (initialState (0,0) (2,2) [(2,2)]) ((-1,-1), (-1,-1), [])
    s1_4 <- runTest "P1-Solapamiento Entre Bloqueos" 0.15 (initialState (0,0) (2,2) [(3,3), (3,3)]) ((-1,-1), (-1,-1), [])
    s1_5 <- runTest "P1-Bloqueo Fuera De Limites" 0.2 (initialState (0,0) (2,2) [(6,1)]) ((-1,-1), (-1,-1), [])
    let totalP1 = s1_0 + s1_1 + s1_2 + s1_3 + s1_4 + s1_5

    -- PARTE 2
    putStrLn "\n--- PARTE 2: Validación (2.0 ptos) ---"
    let stP2 = ((2,2), (2,3), [(2,4)])
    s2_0 <- runTest "P2-Estado Invalido" 0.4 (isValidMove ((6,0), (2,2), []) U) False
    s2_1 <- runTest "P2-Mover a Vacío" 0.4 (isValidMove stP2 U) True
    s2_2 <- runTest "P2-Empujar Caja Falla (Doble Caja)" 0.4 (isValidMove stP2 R) False
    s2_3 <- runTest "P2-Empujar Target Valido" 0.4 (isValidMove ((2,2), (2,3), []) R) True
    s2_4 <- runTest "P2-Empuje Fuera Del Tablero" 0.4 (isValidMove ((4,4), (5,4), []) D) False
    let totalP2 = s2_0 + s2_1 + s2_2 + s2_3 + s2_4

    -- PARTE 3
    putStrLn "\n--- PARTE 3: Ejecución (2.0 ptos) ---"
    let stP3 = ((2,2), (2,3), [(4,4)])
    s3_0 <- runTest "P3-Movimiento Invalido No Cambia Estado" 0.4 (applyMove ((2,2), (2,3), [(2,4)]) R) ((2,2), (2,3), [(2,4)])
    s3_1 <- runTest "P3-Mover Robot Solo" 0.4 (applyMove stP3 D) ((3,2), (2,3), [(4,4)])
    s3_2 <- runTest "P3-Empujar Target" 0.4 (applyMove stP3 R) ((2,3), (2,4), [(4,4)])
    
    let stP3_Obst1 = ((4,3), (1,1), [(4,4)])
    let stP3_Obst2 = ((3,4), (1,1), [(4,4)])
    s3_3 <- runTest "P3-Empujar Bloqueo Horizontal" 0.4 (applyMove stP3_Obst1 R) ((4,4), (1,1), [(4,5)])
    s3_4 <- runTest "P3-Empujar Bloqueo Vertical" 0.4 (applyMove stP3_Obst2 D) ((4,4), (1,1), [(5,4)])
    let totalP3 = s3_0 + s3_1 + s3_2 + s3_3 + s3_4

    -- PARTE 4
    putStrLn "\n--- PARTE 4: Algoritmo BFS (14.0 ptos) ---"
    
    let p4Invalid = ((-1,0), (5,4), [])
    s4_0 <- validateWarehouse "P4-Estado Inicial Invalido" 1.0 p4Invalid (solveWarehouse p4Invalid) 0

    let p4Solved = ((5,4), (5,5), [])
    s4_1 <- validateWarehouse "P4-Ya Resuelto" 1.0 p4Solved (solveWarehouse p4Solved) 0

    -- Caso 1: A un paso de ganar (Robot empuja a la derecha)
    let p4Easy = ((5,3), (5,4), [])
    s4_2 <- validateWarehouse "P4-Fácil (1 Paso)" 1.5 p4Easy (solveWarehouse p4Easy) 1

    -- Caso 2: Rodear la caja para ubicarse y empujar hacia la meta
    let p4Med1 = ((4,4), (4,5), [])
    s4_3 <- validateWarehouse "P4-Medio (Rodear Caja)" 2.5 p4Med1 (solveWarehouse p4Med1) 3

    let p4Alt = ((4,3), (4,4), [])
    s4_4 <- validateWarehouse "P4-Medio (Ruta Alternativa No Minima)" 2.5 p4Alt (solveWarehouse p4Alt) 4

    -- Caso 3: Obstáculo bloqueando la meta
    let p4Med2 = ((3,4), (4,4), [(5,4)])
    s4_5 <- validateWarehouse "P4-Medio (Despejar Obstáculo)" 2.5 p4Med2 (solveWarehouse p4Med2) 6

    -- Caso 4: Sin Solución (Acorralado)
    let p4Hard = ((0,0), (0,1), [(0,2), (1,1), (1,2)])
    s4_6 <- validateWarehouse "P4-Imposible (Atrapado)" 3.0 p4Hard (solveWarehouse p4Hard) 0

    let totalP4 = s4_0 + s4_1 + s4_2 + s4_3 + s4_4 + s4_5 + s4_6

    -- RESUMEN
    putStrLn "\n================================================"
    putStrLn $ "Parte 1:  " ++ formatScore totalP1 ++ " / 1.0"
    putStrLn $ "Parte 2:  " ++ formatScore totalP2 ++ " / 2.0"
    putStrLn $ "Parte 3:  " ++ formatScore totalP3 ++ " / 2.0"
    putStrLn $ "Parte 4:  " ++ formatScore totalP4 ++ " / 14.0"
    putStrLn "------------------------------------------------"
    let grandTotal = totalP1 + totalP2 + totalP3 + totalP4
    putStrLn $ "TOTAL:    " ++ formatScore grandTotal ++ " / 19.0"