use std::collections::{HashMap, HashSet};
use std::convert::TryInto;
use std::hash::{Hash, Hasher};

use rand::seq::SliceRandom;
use strum::IntoEnumIterator;

use crate::min_max::*;

#[derive(Eq, PartialEq)]
#[derive(Debug, Copy, Clone)]
#[repr(u8)]
pub enum CellState {
    EMPTY,
    GREEN,
    YELLOW,
    RED,
}

type Cells = [CellState; 9];

impl Hash for CellState {
    fn hash<H: Hasher>(&self, state: &mut H) {
        state.write_u8(*self as u8)
    }
}

#[derive(Debug)]
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
                Player::Min => BoardStatus::MinWon,
                Player::Max => BoardStatus::MaxWon,
            }
        }
    }

    pub fn empty() -> Board {
        Self::new([CellState::EMPTY; 9], Player::Max)
    }

    pub fn new(cells: [CellState; 9], last_player: Player) -> Board {
        Board { cells, last_player }
    }

    pub fn from_string(str: &String) -> Option<Board> {
        str.split(",").filter_map(|s| match s {
            "e" => Some(CellState::EMPTY),
            "g" => Some(CellState::GREEN),
            "y" => Some(CellState::YELLOW),
            "r" => Some(CellState::RED),
            _ => None,
        }).collect::<Vec<CellState>>()
            .try_into()
            .map(|cells| Board::new(cells, Player::Max))
            .ok()
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
    cache: HashMap<Cells, i32>
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
            if cell_state == CellState::RED {
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
                debug_assert_eq!(state.last_player, Player::Max);
                debug_assert_eq!(player, Player::Min);
                debug_assert_ne!(state.last_player, player);
                -1
            }
            BoardStatus::MinWon => {
                debug_assert_eq!(state.last_player, Player::Min);
                debug_assert_eq!(player, Player::Max);
                debug_assert_ne!(state.last_player, player);
                -1
            }
            BoardStatus::Ongoing => 0
        }
    }

    fn do_move(&self, state: &mut Board, &SymmetricMove(index, _): &SymmetricMove, player: Player) {
        state.cells[index] = match state.cells[index] {
            CellState::EMPTY => CellState::GREEN,
            CellState::GREEN => CellState::YELLOW,
            CellState::YELLOW => CellState::RED,
            CellState::RED => panic!(),
        };
        state.last_player = player;
    }

    fn undo_move(&self, state: &mut Board, &SymmetricMove(index, _): &SymmetricMove, player: Player) {
        state.cells[index] = match state.cells[index] {
            CellState::GREEN => CellState::EMPTY,
            CellState::YELLOW => CellState::GREEN,
            CellState::RED => CellState::YELLOW,
            CellState::EMPTY => panic!(),
        };
        debug_assert_eq!(state.last_player, player);
        state.last_player = !player;
    }

    fn cache(&mut self, state: &Board, score: i32) {
        debug_assert_eq!(None, self.cache.insert(state.cells, score))
    }

    fn lookup(&mut self, state: &Board) -> Option<i32> {
        self.cache.get(&state.cells).cloned()
    }
}

pub fn choose_random_move(moves: Vec<ScoredMove<SymmetricMove>>) -> ScoredMove<usize> {
    all_move_indices(moves)
        .choose(&mut rand::thread_rng())
        .unwrap()
        .clone()
}

pub fn all_move_indices(moves: Vec<ScoredMove<SymmetricMove>>) -> Vec<ScoredMove<usize>> {
    moves.iter()
        .flat_map(|m| m.min_max_move.expanded_index().into_iter().map(move |i| ScoredMove::new(m.score, i)))
        .collect::<HashSet<_>>()
        .into_iter()
        .collect::<Vec<_>>()
}

#[cfg(test)]
mod tests {
    use crate::min_max::Player;
    use crate::stoplight::{Board, CellState, choose_random_move, print_3_by_3, Strategie, Cells, to_score_board};

    fn best_move_index_of(cells: [CellState; 9]) -> usize {
        let m = Strategie::new().as_strategie().alpha_beta(&mut Board::new(cells, Player::Max), 30);
        print_3_by_3(&to_score_board(&m));
        return m[0].min_max_move.index();
    }

    fn score_board(cells: Cells) -> [i32; 9] {
        to_score_board(&Strategie::new().as_strategie().all_moves_scored(&mut Board::new(cells, Player::Max), 30))
    }

    #[test]
    fn two_green() {
        {
            let cells = [
                CellState::EMPTY, CellState::EMPTY, CellState::GREEN,
                CellState::GREEN, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
            ];
            let scores = score_board(cells);
            print_3_by_3(&scores)
        }
        {
            let cells = [
                CellState::EMPTY, CellState::GREEN, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::GREEN,
            ];
            let scores = score_board(cells);
            print_3_by_3(&scores)
        }
        {
            let cells = [
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::GREEN,
                CellState::GREEN, CellState::EMPTY, CellState::EMPTY,
            ];
            let scores = score_board(cells);
            print_3_by_3(&scores)
        }
        {
            let cells = [
                CellState::GREEN, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::GREEN, CellState::EMPTY,
            ];
            let scores = score_board(cells);
            print_3_by_3(&scores)
        }
    }

    #[test]
    fn empty_board() {
        fn score_and_print(cells: Cells) {
            let score_board = score_board(cells);
            print_3_by_3(&score_board);
        }
        {
            let cells = [CellState::EMPTY; 9];
            score_and_print(cells);
        }
        println!("=> Ki plays 0");
        {
            let cells = [
                CellState::GREEN, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
            ];
            score_and_print(cells);
        }
        println!("=> Human plays 0");
        {
            let cells = [
                CellState::YELLOW, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
            ];
            score_and_print(cells);
        }
        println!("=> Ki plays 0");
        {
            let cells = [
                CellState::RED, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
            ];
            score_and_print(cells);
        }
        println!("=> Human plays 1");
        {
            let cells = [
                CellState::RED, CellState::GREEN, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
            ];
            score_and_print(cells);
        }
        println!("=> Ki plays 1");
        {
            let cells = [
                CellState::RED, CellState::YELLOW, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
                CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
            ];
            score_and_print(cells);
        }
    }
}