mod min_max;
mod stoplight;
mod misery;

use std::env;

use std::time::{Instant};
use crate::stoplight::{Board, choose_random_move};

fn main() {
    let now = Instant::now();
    let args: Vec<String> = env::args().collect();
    if args.len() < 2 {
        eprintln!("missing argument 'board'");
        println!("-1");
        return;
    }
    let board_str = &args[1];
    match Board::from_string(board_str) {
        Some(mut board) => {
            let moves = stoplight::Strategie::new().as_strategie().alpha_beta(&mut board, 30);
            let m = choose_random_move(moves);
            println!("{}", m.min_max_move);
            eprintln!("score for current move is {}", m.score);
        },
        None => {
            eprintln!("invalid board '{}'", board_str);
            println!("-1");
        }
    }
    eprintln!("took {}ms", now.elapsed().as_millis())
}
