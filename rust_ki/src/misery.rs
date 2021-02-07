use std::collections::HashMap;
use std::hash::{Hash, Hasher};

use strum::IntoEnumIterator;

use crate::min_max::*;

#[derive(Eq, PartialEq, Hash)]
#[derive(Debug, Copy, Clone)]
#[repr(u8)]
pub enum CellState {
    EMPTY,
    X,
}

type Cells = [CellState; 9];

#[derive(Debug, Eq, PartialEq, Hash, Clone)]
pub struct Board {
    pub cells: Cells,
    last_player: Player,
}

#[derive(Eq, PartialEq)]
#[derive(Debug, Copy, Clone)]
pub enum BoardStatus {
    MaxWon,
    MinWon,
    Ongoing,
}

impl Board {
    fn symmetries(&self) -> Vec<Symmetrie> {
        Symmetrie::iter().filter(|symmetry| {
            symmetry.symmetric_pairs().iter().all(|(f, s)| self.cells[*f as usize] == self.cells[*s as usize])
        }).collect()
    }

    fn status(&self) -> BoardStatus {
        let winning_indices = Self::WIN_INDICES.iter().find(|indices| {
            self.cells[indices[0]] == self.cells[indices[1]] && self.cells[indices[1]] == self.cells[indices[2]] && self.cells[indices[0]] != CellState::EMPTY
        });
        match winning_indices {
            None => BoardStatus::Ongoing,
            Some(_indices) => match self.last_player {
                Player::Min => BoardStatus::MaxWon,
                Player::Max => BoardStatus::MinWon,
            }
        }
    }

    pub fn empty() -> Board {
        Self::new([CellState::EMPTY; 9], Player::Max)
    }

    pub fn new(cells: [CellState; 9], last_player: Player) -> Board {
        Board { cells, last_player }
    }

    const WIN_INDICES: [[usize; 3]; 8] = [
        [0, 1, 2],
        [3, 4, 5],
        [6, 7, 8],
        [0, 3, 6],
        [1, 4, 7],
        [2, 5, 8],
        [0, 4, 8],
        [2, 4, 6],
    ];
}

pub struct Strategie {
    cache: HashMap<Board, CacheEntry>
}

impl Strategie {
    pub fn new() -> Strategie {
        Strategie { cache: HashMap::new() }
    }

    pub fn as_strategie(&mut self) -> &mut (dyn MinMaxStrategie<Board, SymmetricMove>) {
        self
    }
}

impl MinMaxStrategie<Board, SymmetricMove> for Strategie {
    fn possible_moves(&self, state: &Board) -> Vec<SymmetricMove> {
        let symmetries = state.symmetries();
        let mut covered_index = [false; 9];
        let mut moves = Vec::new();
        for (i, &cell_state) in state.cells.iter().enumerate() {
            if cell_state == CellState::X {
                continue;
            }
            let normalised = normalise(&symmetries, i);
            if covered_index[normalised] {
                continue;
            }
            covered_index[normalised] = true;
            moves.push(SymmetricMove(i, symmetries.to_vec()))
        }
        moves
    }

    fn is_terminal(&self, state: &Board) -> bool {
        state.status() != BoardStatus::Ongoing
    }

    fn score(&self, state: &Board, player: Player) -> i32 {
        match state.status() {
            BoardStatus::MaxWon => {
                debug_assert_eq!(player, Player::Max);
                1
            }
            BoardStatus::MinWon => {
                debug_assert_eq!(player, Player::Min);
                1
            }
            BoardStatus::Ongoing => 0,
        }
    }

    fn do_move(&self, state: &mut Board, min_max_move: &SymmetricMove, player: Player) {
        debug_assert_eq!(state.cells[min_max_move.index()], CellState::EMPTY);
        state.cells[min_max_move.index()] = CellState::X;
        state.last_player = player;
    }

    fn undo_move(&self, state: &mut Board, min_max_move: &SymmetricMove, player: Player) {
        debug_assert_eq!(state.cells[min_max_move.index()], CellState::X);
        state.cells[min_max_move.index()] = CellState::EMPTY;
        state.last_player = !player;
    }

    fn cache(&mut self, state: &Board, entry: CacheEntry) {
        self.cache.insert(state.clone(), entry);
    }

    fn lookup(&mut self, state: &Board) -> Option<CacheEntry> {
        self.cache.get(&state).cloned()
    }
}

#[cfg(test)]
mod test {
    use crate::min_max::{Player, print_3_by_3, to_score_board};

    use super::*;

    fn cells_to_score_board(cells: Cells) -> [i32; 9] {
        let moves = Strategie::new().as_strategie().all_moves_scored(&mut Board::new(cells, Player::Max), 10);
        to_score_board(&moves)
    }

    #[test]
    fn empty() {
        let scores = cells_to_score_board([CellState::EMPTY; 9]);
        let sines = [
            -1, -1, -1,
            -1, 1, -1,
            -1, -1, -1,
        ];
        expect_sines(scores, sines);
    }

    #[test]
    fn nearly_won() {
        let cells = [
            CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
            CellState::X, CellState::EMPTY, CellState::X,
            CellState::X, CellState::EMPTY, CellState::X,
        ];
        let scores = cells_to_score_board(cells);
        assert_eq!(1, scores.iter().enumerate().max_by_key(|&(_i, score)| *score).unwrap().0);
    }

    fn expect_sines(scores: [i32; 9], signs: [i8; 9]) {
        let panic = |i: usize| {
            print_3_by_3(&scores);
            print_3_by_3(&signs);
            panic!("sign at index {} differs", i);
        };
        for (i, &s) in scores.iter().enumerate() {
            if s < 0 && signs[i] >= 0 {
                panic(i);
            } else if s > 0 && signs[i] <= 0 {
                panic(i);
            } else if s == 0 && signs[i] != 0 {
                panic(i);
            }
        }
    }

    #[test]
    fn half_way_won() {
        {
            let cells = [
                CellState::X, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::X, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
            ];
            let sines = [
                0, -1, 1,
                -1, 0, 1,
                1, 1, -1,
            ];
            let scores = cells_to_score_board(cells);
            expect_sines(scores, sines);
        }
        {
            let cells = [
                CellState::EMPTY, CellState::EMPTY, CellState::X,
                CellState::EMPTY, CellState::X, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
            ];
            let sines = [
                1, -1, 0,
                1, 0, -1,
                -1, 1, 1,
            ];
            let scores = cells_to_score_board(cells);
            expect_sines(scores, sines);
        }
    }

    #[test]
    fn win_is_imminent_question_mark() {
        {
            let cells = [
                CellState::X, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::X, CellState::EMPTY,
                CellState::EMPTY, CellState::X, CellState::EMPTY,
            ];
            let sines = [
                0, -1, -1,
                -1, 0, -1,
                -1, 0, -1,
            ];
            let scores = cells_to_score_board(cells);
            expect_sines(scores, sines);
        }
        {
            let cells = [
                CellState::X, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::X, CellState::X,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
            ];
            let sines = [
                0, -1, -1,
                -1, 0, 0,
                -1, -1, -1,
            ];
            let scores = cells_to_score_board(cells);
            expect_sines(scores, sines);
        }
    }

    #[test]
    fn complete_cube_for_win() {
        let cells = [
            CellState::X, CellState::X, CellState::EMPTY,
            CellState::EMPTY, CellState::X, CellState::EMPTY,
            CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
        ];
        let sines = [
            0, 0, -1,
            1, 0, -1,
            -1, -1, -1
        ];
        let scores = cells_to_score_board(cells);
        expect_sines(scores, sines);
    }

    #[test]
    fn why_does_complete_cube_for_win_fail() {
        let cells = [
            CellState::X, CellState::X, CellState::EMPTY,
            CellState::EMPTY, CellState::X, CellState::X,
            CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
        ];
        let sines = [
            0, 0, -1,
            -1, 0, 0,
            1, -1, -1
        ];
        let scores = cells_to_score_board(cells);
        expect_sines(scores, sines);
    }
}
