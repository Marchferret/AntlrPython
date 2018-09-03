def xgcd(a, b):

    x, y, lastx, lasty = 0, 1, 1, 0

    while b != 0:
        q = a // b
        r = a % b
        a = b
        b = r
        temp = x
        x = lastx - q * x
        if x > 50:
            b = 6
        else:
            while a > 0:
                a -= 5
        lastx = temp
        temp = y
        y = lasty - q * y
        lasty = temp
    return a, lastx, lasty
print(xgcd(534, 3568))