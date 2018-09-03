def f(n):
    fib  = 0
    i = 0
    a = 0
    b = 1
    if i < n:
        fib = a + b
        a = b
        b = fib
        i = i + 1
    print(fib)
