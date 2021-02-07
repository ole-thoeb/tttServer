use std::cmp::Ordering;
use std::collections::HashSet;
use std::ops::Not;

use strum_macros::EnumIter;
use std::hash::{Hash, Hasher};
use std::fmt::{Debug, Display};
use std::any::{Any, TypeId};
use crate::stoplight::{Board, CellState};

#[derive(Debug)]
#[derive(Eq, PartialEq)]
#[derive(Copy, Clone)]
pub enum Player {
    Min,
    Max,
}

#[derive(Debug, Eq, PartialEq)]
pub struct ScoredMove<M> {
    pub score: i32,
    pub min_max_move: M,
}

impl<M> ScoredMove<M> {
    pub fn new(score: i32, min_max_move: M) -> ScoredMove<M> {
        ScoredMove { score, min_max_move }
    }
}

impl<M: Clone> Clone for ScoredMove<M> {
    fn clone(&self) -> Self {
        ScoredMove::new(self.score, self.min_max_move.clone())
    }
}

impl<M: Hash> Hash for ScoredMove<M> {
    fn hash<H: Hasher>(&self, state: &mut H) {
        state.write_i32(self.score);
        self.min_max_move.hash(state);
    }
}

impl Not for Player {
    type Output = Player;

    fn not(self) -> Player {
        match self {
            Player::Min => Player::Max,
            Player::Max => Player::Min,
        }
    }
}

pub trait MinMaxStrategie<S, M> {
    fn possible_moves(&self, state: &S) -> Vec<M>;
    fn is_terminal(&self, state: &S) -> bool;
    fn score(&self, state: &S, player: Player) -> i32;
    fn do_move(&self, state: &mut S, min_max_move: &M, player: Player);
    fn undo_move(&self, state: &mut S, min_max_move: &M, player: Player);
    fn cache(&mut self, state: &S, score: i32);
    fn lookup(&mut self, state: &S) -> Option<i32>;
}

impl<'a, S: Debug + Any, M: Clone + Debug> dyn MinMaxStrategie<S, M> + 'a {
    pub fn alpha_beta(&mut self, state: &mut S, max_level: u8) -> Vec<ScoredMove<M>> {
        all_max(self.all_moves_scored(state, max_level).into_iter(), |a, b| a.score.cmp(&b.score)).unwrap()
    }

    pub fn all_moves_scored(&mut self, state: &mut S, max_level: u8) -> Vec<ScoredMove<M>> {
        let mut max_score = -i32::MAX;
        self.possible_moves(&state).into_iter().map(|m| {
            self.do_move(state, &m, Player::Max);
            let score = -self.alpha_beta_eval(state, Player::Min, max_level - 1, -i32::MAX, -max_score);
            self.undo_move(state, &m, Player::Max);
            if score > max_score {
                max_score = score;
            }
            ScoredMove::new(score, m)
        }).collect()
    }

    fn alpha_beta_eval(&mut self, state: &mut S, player: Player, level: u8, alpha: i32, beta: i32) -> i32 {
        match self.lookup(state) {
            Some(score) => {
                // if level == 28 {
                //     eprintln!("cache hit {} <- {:?}", score, state)
                // }
                score
            },
            None => {
                if self.is_terminal(state) || level == 0 {
                    // if level == 28 {
                    //     eprintln!("is_terminal {:?}", state)
                    // }
                    self.score(state, player) * (i32::from(level) + 1)
                } else {
                    let mut max_score = alpha;
                    for m in self.possible_moves(state) {
                        self.do_move(state, &m, player);
                        let score = -self.alpha_beta_eval(state, !player, level - 1, -beta, -max_score);
                        // if level == 29 {
                        //     eprintln!("{} <- state {:?}", score, state);
                        // }
                        self.undo_move(state, &m, player);
                        if score > max_score {
                            max_score = score;
                            if max_score >= beta {
                                break;
                            }
                        }
                    }
                    self.cache(state, max_score);
                    max_score
                }
            }
        }
    }
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


#[derive(Eq, PartialEq)]
#[derive(Debug)]
pub struct SymmetricMove(pub usize, pub Vec<Symmetrie>);

impl SymmetricMove {
    pub fn index(&self) -> usize {
        self.0
    }

    pub fn expanded_index(&self) -> Vec<usize> {
        let SymmetricMove(index, symmetries) = self;
        if symmetries.is_empty() {
            vec![*index]
        } else {
            symmetries.iter()
                .flat_map(|symmetry| symmetry.mirror(*index))
                .collect::<HashSet<usize>>()
                .into_iter()
                .collect()
        }
    }
}

impl Clone for SymmetricMove {
    fn clone(&self) -> Self {
        SymmetricMove(self.index(), self.1.clone())
    }
}

#[derive(EnumIter, Eq, PartialEq, Debug, Clone)]
pub enum Symmetrie {
    YAxes,
    XAxes,
    TopBottom,
    BottomTop,
}

impl Symmetrie {
    pub fn symmetric_pairs(&self) -> [(u8, u8); 3] {
        match self {
            Symmetrie::YAxes => [(0, 2), (3, 5), (6, 8)],
            Symmetrie::XAxes => [(0, 6), (1, 7), (2, 8)],
            Symmetrie::TopBottom => [(1, 3), (2, 6), (5, 7)],
            Symmetrie::BottomTop => [(0, 8), (1, 5), (3, 7)],
        }
    }

    pub fn mirror(&self, index: usize) -> Vec<usize> {
        self.symmetric_pairs().iter()
            .find(|(f, s)| *f as usize == index || *s as usize == index)
            .map(|(f, s)| vec![*f as usize, *s as usize])
            .unwrap_or_else(|| vec![index])
    }
}

pub fn normalise(symmetries: &Vec<Symmetrie>, index: usize) -> usize {
    let mut normalised = index;
    for symm in symmetries {
        for &(first, second) in symm.symmetric_pairs().iter() {
            if usize::from(second) == normalised {
                normalised = usize::from(first)
            }
        }
    }
    return normalised;
}

pub fn to_score_board(scored_moves: &Vec<ScoredMove<SymmetricMove>>) -> [i32; 9] {
    let mut scores = [0; 9];
    for m in scored_moves.iter() {
        for index in m.min_max_move.expanded_index() {
            scores[index] = m.score
        }
    }
    scores
}

pub fn print_3_by_3<E: Display>(scored_board: &[E; 9]) {
    let scores = scored_board;
    eprintln!("{:>3}, {:>3}, {:>3}", scores[0], scores[1], scores[2]);
    eprintln!("{:>3}, {:>3}, {:>3}", scores[3], scores[4], scores[5]);
    eprintln!("{:>3}, {:>3}, {:>3}", scores[6], scores[7], scores[8]);
}
