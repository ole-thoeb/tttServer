mod min_max;
mod stoplight;

use std::time::{Instant};
fn main() {
    let strategie = stoplight::STRATEGIE;
    let now = Instant::now();
    let res = strategie.alpha_beta(&mut stoplight::Board::empty(), 19);
    println!("{:?} took {}ms", res, now.elapsed().as_millis())
}
