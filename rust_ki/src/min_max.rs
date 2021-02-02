use std::ops::Not;

use strum_macros::EnumIter;

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
    fn new(score: i32, min_max_move: M) -> ScoredMove<M> {
        ScoredMove { score, min_max_move }
    }
}

impl Not for Player {
    type Output = Player;

    fn not(self) -> Player {
        match self {
            Player::Min => Player::Max,
            Player::Max => Player::Max,
        }
    }
}

pub trait MinMaxStrategie<S, M> {
    fn possible_moves(&self, state: &S) -> Vec<M>;
    fn is_terminal(&self, state: &S) -> bool;
    fn score(&self, state: &S, player: Player) -> i32;
    fn do_move(&self, state: &mut S, min_max_move: &M, player: Player);
    fn undo_move(&self, state: &mut S, min_max_move: &M, player: Player);
}

impl<S, M> dyn MinMaxStrategie<S, M> {
    pub fn alpha_beta(&self, state: &mut S, max_level: u32) -> ScoredMove<M> {
        let weighted_moves = self.possible_moves(&state).into_iter().map(|m| {
            self.do_move(state, &m, Player::Max);
            let score = -self.alpha_beta_eval(state, Player::Min, max_level - 1, -i32::MAX, i32::MAX);
            self.undo_move(state, &m, Player::Max);
            ScoredMove::new(score, m)
        });
        weighted_moves.max_by(|a, b| a.score.cmp(&b.score)).unwrap()
    }

    fn alpha_beta_eval(&self, state: &mut S, player: Player, level: u32, alpha: i32, beta: i32) -> i32 {
        if self.is_terminal(state) || level == 0 {
            self.score(state, player) * (level as i32 + 1)
        } else {
            let mut max_score = alpha;
            for m in self.possible_moves(state) {
                self.do_move(state, &m, player);
                let score = -self.alpha_beta_eval(state, !player, level - 1, -beta, -max_score);
                self.undo_move(state, &m, player);
                if score > max_score {
                    max_score = score;
                    if max_score >= beta {
                        break;
                    }
                }
            }
            max_score
        }
    }
}


#[derive(Eq, PartialEq)]
#[derive(Debug)]
pub struct SymmetricMove(pub usize, pub Vec<Symmetrie>);

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
