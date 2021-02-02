use strum::IntoEnumIterator;

use crate::min_max::*;

#[derive(Eq, PartialEq)]
#[derive(Debug, Copy, Clone)]
pub enum CellState {
    EMPTY,
    GREEN,
    YELLOW,
    RED,
}

pub struct Board {
    cells: [CellState; 9],
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
            Some(_) => match self.last_player {
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


struct Strategie;

pub const STRATEGIE: &'static dyn MinMaxStrategie<Board, SymmetricMove> = &Strategie {};

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
            BoardStatus::MaxWon => if player == Player::Max { 1 } else { -1 }
            BoardStatus::MinWon => if player == Player::Min { 1 } else { -1 }
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
        state.last_player = !player;
    }
}

#[cfg(test)]
mod tests {
    use crate::min_max::Player;
    use crate::stoplight::{Board, CellState, STRATEGIE};

    fn best_move_index_of(cells: [CellState; 9]) -> usize {
        let m = STRATEGIE.alpha_beta(&mut Board::new(cells, Player::Max), u32::MAX);
        return m.min_max_move.0;
    }

    #[test]
    fn best_move_all_yellow() {
        let cells = [
            CellState::YELLOW, CellState::YELLOW, CellState::YELLOW,
            CellState::YELLOW, CellState::YELLOW, CellState::YELLOW,
            CellState::YELLOW, CellState::YELLOW, CellState::YELLOW,
        ];
        assert_eq!(4, best_move_index_of(cells))
    }

    #[test]
    fn best_move_all_green_one_yellow() {
        let cells = [
            CellState::GREEN, CellState::GREEN, CellState::GREEN,
            CellState::YELLOW, CellState::GREEN, CellState::GREEN,
            CellState::GREEN, CellState::GREEN, CellState::GREEN,
        ];
        assert_eq!(5, best_move_index_of(cells))
    }
}