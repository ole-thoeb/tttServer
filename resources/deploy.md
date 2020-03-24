build gradle

build
`sudo docker build -t olethoeb/ttt_server .`
push 
`docker image push olethoeb/ttt_server:latest`
run 
`docker run -m512M --cpus 1 -it -p 8080:8080 --rm olethoeb/ttt_server`