class Phi:

    def __init__(self):
        self.set_once = False
        self.set_twice = False


    def set(self):
        if self.set_once:
            self.set_twice = True
        else:
            self.set_once = True

    def phiEntry(self, init_obj, iter_obj):
        if self.set_twice:
            return iter_obj
        else:
            return init_obj

    def phiLoopTest(self, init_obj, iter_obj):
        if (not self.set_once) and (not self.set_twice):
            return init_obj
        else:
            return iter_obj

    def phiExit(self, init_obj, final_obj):
        if self.set_once:
            return final_obj
        else:
            return init_obj



def phiIf(phiPreds, phiNames):
    if True not in phiPreds:
        return phiNames[len(phiNames) - 1]
    else:

        return phiNames[phiPreds.index(True)]


