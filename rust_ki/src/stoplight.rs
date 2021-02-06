use std::collections::{HashMap, HashSet};
use std::hash::{Hash, Hasher};

use rand::seq::SliceRandom;
use strum::IntoEnumIterator;

use crate::min_max::*;
use std::convert::TryInto;
use std::cmp::Ordering;

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

fn count_occupied_cells(cells: &Cells) -> i32 {
    cells.iter().fold(0, |acc, state| acc + *state as i32)
}

pub struct Board {
    cells: Cells,
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

pub fn compute_all_best_moves(board: &mut Board) -> (Vec<ScoredMove<SymmetricMove>>, HashMap<[CellState; 9], (u8, i32)>) {
    struct Cache { map: HashMap<Cells, (u8, i32)> }
    impl Cache {
        fn insert(&mut self, cells: Cells, info: (u8, i32)) -> Option<(u8, i32)> {
            let res = self.map.insert(cells, info);
            if self.map.len() % 1000 == 0 {
                eprintln!("cached {} items", self.map.len())
            }
            res
        }

        fn get(&self, cells: &Cells) -> Option<&(u8, i32)> {
            self.map.get(cells)
        }

        fn new() -> Cache {
            Cache { map: HashMap::new() }
        }
    }

    fn alpha_beta(state: &mut Board, cache: &mut Cache) -> Vec<ScoredMove<SymmetricMove>> {
        let weighted_moves = STRATEGIE.possible_moves(&state).into_iter().map(|m| {
            STRATEGIE.do_move(state, &m, Player::Max);
            let score = -alpha_beta_eval(state, cache, Player::Min, -i32::MAX, i32::MAX);
            STRATEGIE.undo_move(state, &m, Player::Max);
            ScoredMove::new(score, m)
        }).collect::<Vec<_>>();
        let mut scores = [-1; 9];
        for m in weighted_moves.iter() {
            for index in m.min_max_move.expanded_index() {
                scores[index] = m.score
            }
        }
        eprintln!("{:?}", scores);
        let best_move = all_max(&mut weighted_moves.into_iter(), |a, b| a.score.cmp(&b.score)).unwrap();
        // cache.insert(state.cells, (best_move.min_max_move.index() as u8, best_move.score));
        eprintln!("best move {:?}", best_move);
        best_move
    }

    fn alpha_beta_eval(state: &mut Board, cache: &mut Cache, player: Player, alpha: i32, beta: i32) -> i32 {
        match cache.get(&state.cells)/* None as Option<&(u8, i32)>*/ {
            Some(&(_index, score)) => {
                // println!("cache hit");
                score
            },
            None => {
                if STRATEGIE.is_terminal(state) {
                    // println!("Terminal reached");
                    STRATEGIE.score(state, player) * (27 - count_occupied_cells(&state.cells))
                } else {
                    let mut max_score = alpha;
                    let mut best_move = None;
                    for m in STRATEGIE.possible_moves(state) {
                        STRATEGIE.do_move(state, &m, player);
                        let score = -alpha_beta_eval(state, cache, !player, -beta, -max_score);
                        STRATEGIE.undo_move(state, &m, player);
                        if score > max_score {
                            max_score = score;
                            best_move = Some(m);
                            if max_score >= beta {
                                break;
                            }
                        }
                    }
                    if let Some(m) = best_move {
                        cache.insert(state.cells, (m.index() as u8, max_score));
                    }
                    max_score
                }
            }
        }
    }

    let mut cache = Cache::new();
    let m = alpha_beta(board, &mut cache);
    (m, cache.map)
}

pub fn choose_random_move(moves: Vec<ScoredMove<SymmetricMove>>) -> usize {
    *all_move_indices(moves)
        .choose(&mut rand::thread_rng())
        .unwrap()
}

pub fn all_move_indices(moves: Vec<ScoredMove<SymmetricMove>>) -> Vec<usize> {
    moves.iter()
        .flat_map(|m| m.min_max_move.expanded_index())
        .collect::<HashSet<_>>()
        .into_iter()
        .collect::<Vec<_>>()
}

fn all_max<I, F>(mut iter: I, ordering: F) -> Option<Vec<I::Item>>
    where I: Sized + Iterator,
          I::Item: Clone,
          F: Fn(&I::Item, &I::Item) -> Ordering
{
    match iter.next() {
        None => None,
        Some(first) => {
            let mut max = first.clone();
            let mut maxs = vec![first];
            for item in iter {
                match ordering(&max, &item) {
                    Ordering::Less => {
                        max = item.clone();
                        maxs = vec![item];
                    }
                    Ordering::Equal => {
                        maxs.push(item);
                    }
                    Ordering::Greater => {}
                }
            }
            Some(maxs)
        }
    }
}

#[cfg(test)]
mod tests {
    use crate::min_max::Player;
    use crate::stoplight::{Board, CellState, STRATEGIE, compute_all_best_moves, choose_random_move, all_move_indices};

    fn best_move_index_of(cells: [CellState; 9]) -> usize {
        let m = STRATEGIE.alpha_beta(&mut Board::new(cells, Player::Max), u8::MAX);
        return m.min_max_move.index();
    }

    fn best_move_index_cached_of(cells: [CellState; 9]) -> usize {
        let (m, _cache) = compute_all_best_moves(&mut Board::new(cells, Player::Max));
        return m[0].min_max_move.index();
    }

    #[test]
    fn best_move_all_yellow() {
        let cells = [
            CellState::YELLOW, CellState::YELLOW, CellState::YELLOW,
            CellState::YELLOW, CellState::YELLOW, CellState::YELLOW,
            CellState::YELLOW, CellState::YELLOW, CellState::YELLOW,
        ];
        assert_eq!(4, best_move_index_of(cells));
        assert_eq!(4, best_move_index_cached_of(cells));
    }

    #[test]
    fn best_move_all_green_one_yellow() {
        let cells = [
            CellState::GREEN, CellState::GREEN, CellState::GREEN,
            CellState::YELLOW, CellState::GREEN, CellState::GREEN,
            CellState::GREEN, CellState::GREEN, CellState::GREEN,
        ];
        assert_eq!(5, best_move_index_of(cells));
        assert_eq!(5, best_move_index_cached_of(cells));
    }

    #[test]
    fn expanding_with_no_symmetries() {
        let cells = [
            CellState::EMPTY, CellState::GREEN, CellState::EMPTY,
            CellState::EMPTY, CellState::RED, CellState::RED,
            CellState::EMPTY, CellState::EMPTY, CellState::EMPTY,
        ];
        let (m, _cache) = compute_all_best_moves(&mut Board::new(cells, Player::Max));
        choose_random_move(m);
    }

    #[test]
    fn empty_board() {
        let (m, _cache) = compute_all_best_moves(&mut Board::empty());
        println!("{:?}", all_move_indices(m));
    }
}