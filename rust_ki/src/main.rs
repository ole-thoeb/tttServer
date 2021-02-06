mod min_max;
mod stoplight;
use std::env;
use rand::seq::SliceRandom;

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
            let (res, _cache) = stoplight::compute_all_best_moves(&mut board);
            println!("{}", choose_random_move(res))
        },
        None => {
            eprintln!("invalid board '{}'", board_str);
            println!("-1");
        }
    }
    eprintln!("took {}ms", now.elapsed().as_millis())
}
