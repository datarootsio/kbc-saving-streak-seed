PORT ?= 3333
DATA_DIR ?= $(CURDIR)/data/data-cleaning

.PHONY: help build run test server-test lint clean

help:
	@echo "make build        Install frontend dependencies and compile the app"
	@echo "make run          Start at http://127.0.0.1:$(PORT)"
	@echo "make test         Run Java tests"
	@echo "make server-test  Run application tests"
	@echo "make lint         Apply Java formatting"
	@echo "make clean        Remove generated build output"

build:
	./refine build

run:
	./refine -i 127.0.0.1 -p $(PORT) -d "$(DATA_DIR)" -x refine.headless=true

test:
	./refine test

server-test:
	./refine server_test

lint:
	./refine lint

clean:
	./refine clean
