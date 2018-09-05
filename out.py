from phi import *
import linecache
import traceback
from . import base_futures
from . import coroutines
def _task_repr_info(task):
    task_0 = task;
    coro_0=None;info_0=None;

    info_0=base_futures._future_repr_info(task_0) 
    if task_0._must_cancel:
        info_0[0]='cancelling' 
    coro_0=coroutines._format_coroutine(task_0._coro) 
    info_0.insert(1,'coro=<%s>'%coro_0) 
    if task_0._fut_waiter is  not None:
        info_0.insert(2,'wait_for=%r'%task_0._fut_waiter) 
    return info_0

def _task_get_stack(task,limit):
    task_1 = task;limit_0 = limit;
    frames_0=None;f_0=None;f_2=None;f_1=None;f_3=None;f_4=None;limit_3=None;limit_1=None;limit_2=None;limit_4=None;limit_7=None;limit_5=None;limit_6=None;limit_8=None;limit_9=None;tb_0=None;tb_2=None;tb_1=None;tb_3=None;tb_4=None;

    frames_0=[] 
    try:
        f_0=task_1._coro.cr_frame 
    except AttributeError:
        pass
    if f_0 is  not None:
        phi0 = Phi()
        while phi0.phiLoopTest(f_0,f_1) is  not None:
            phi0.set()
            f_2 = phi0.phiEntry(f_0,f_1)
            limit_3 = phi0.phiEntry(limit_0,limit_2)

            if limit_3 is  not None:
                if limit_3<=0:
                    break
                limit_1 = limit_3-1
            phiPreds = [limit_3 is  not None]
            phiNames = [limit_1,limit_3]
            limit_2= phiIf(phiPreds, phiNames)
            frames_0.append(f_2) 
            f_1=f_2.f_back 
        f_3 = phi0.phiExit(f_0,f_1)
        limit_4 = phi0.phiExit(limit_0,limit_2)
        frames_0.reverse() 
    elif task_1._exception is  not None:
        tb_0=task_1._exception.__traceback__ 
        phi0 = Phi()
        while phi0.phiLoopTest(tb_0,tb_1) is  not None:
            phi0.set()
            limit_7 = phi0.phiEntry(limit_0,limit_6)
            tb_2 = phi0.phiEntry(tb_0,tb_1)

            if limit_7 is  not None:
                if limit_7<=0:
                    break
                limit_5 = limit_7-1
            phiPreds = [limit_7 is  not None]
            phiNames = [limit_5,limit_7]
            limit_6= phiIf(phiPreds, phiNames)
            frames_0.append(tb_2.tb_frame) 
            tb_1=tb_2.tb_next 
        limit_8 = phi0.phiExit(limit_0,limit_6)
        tb_3 = phi0.phiExit(tb_0,tb_1)
    phiPreds = [f_0 is  not None,task_1._exception is  not None]
    phiNames = [f_3,f_0,f_0]
    f_4= phiIf(phiPreds, phiNames)
    phiPreds = [f_0 is  not None,task_1._exception is  not None]
    phiNames = [limit_4,limit_8,limit_0]
    limit_9= phiIf(phiPreds, phiNames)
    phiPreds = [f_0 is  not None,task_1._exception is  not None]
    phiNames = [None,tb_3,None]
    tb_4= phiIf(phiPreds, phiNames)
    return frames_0

def _task_print_stack(task,limit,file):
    task_2 = task;limit_10 = limit;file_0 = file;
    extracted_list_0=None;exc_0=None;lineno_1=None;lineno_0=None;lineno_2=None;filename_1=None;filename_0=None;filename_2=None;line_1=None;line_0=None;line_2=None;name_1=None;name_0=None;name_2=None;checked_0=None;co_1=None;co_0=None;co_2=None;

    extracted_list_0=[] 
    checked_0=set() 
    phi0 = Phi()
    for f_5 in task_2.get_stack(limit=limit_10):
        phi0.set()
        lineno_1 = phi0.phiEntry(None,lineno_0)
        filename_1 = phi0.phiEntry(None,filename_0)
        line_1 = phi0.phiEntry(None,line_0)
        name_1 = phi0.phiEntry(None,name_0)
        co_1 = phi0.phiEntry(None,co_0)

        lineno_0=f_5.f_lineno 
        co_0=f_5.f_code 
        filename_0=co_0.co_filename 
        name_0=co_0.co_name 
        if filename_0 not  in checked_0:
            checked_0.add(filename_0) 
            linecache.checkcache(filename_0) 
        line_0=linecache.getline(filename_0,lineno_0,f_5.f_globals) 
        extracted_list_0.append((filename_0,lineno_0,name_0,line_0)) 
    lineno_2 = phi0.phiExit(None,lineno_0)
    filename_2 = phi0.phiExit(None,filename_0)
    line_2 = phi0.phiExit(None,line_0)
    name_2 = phi0.phiExit(None,name_0)
    co_2 = phi0.phiExit(None,co_0)
    exc_0=task_2._exception 
    if  not extracted_list_0:
        print() 
    elif exc_0 is  not None:
        print() 
    else:
        print() 
        traceback.print_list(extracted_list_0,file=file_0) 
    if exc_0 is  not None:
        phi0 = Phi()
        for line_3 in traceback.format_exception_only(exc_0.__class__,exc_0):
            phi0.set()

            print() 



#generate python causal map
causal_map = {'name_2':['name_0','task_2','limit','limit_10','f_5'],'lineno_1':['lineno_0','task_2','limit','limit_10','f_5'],'name_1':['name_0','task_2','limit','limit_10','f_5'],'lineno_0':['f_5'],'name_0':['co_0'],'exc_0':['task_2'],'limit_9':['limit_4','limit_8','limit_0','f_0','task_1'],'limit_8':['limit_0','limit_6','tb_1','tb_0'],'limit_7':['limit_0','limit_6','tb_1','tb_0'],'limit_6':['limit_5','limit_7','limit_7'],'lineno_2':['lineno_0','task_2','limit','limit_10','f_5'],'limit_5':['limit_7'],'limit_4':['limit_0','limit_2','f_0','f_1'],'limit_3':['limit_0','limit_2','f_0','f_1'],'limit_2':['limit_1','limit_3','limit_3'],'checked_0':[],'limit_1':['limit_3'],'tb_3':['tb_0','tb_1','tb_1','tb_0'],'tb_4':['tb_3','f_0','task_1'],'f_0':['task_1'],'tb_1':['tb_2'],'tb_2':['tb_0','tb_1','tb_1','tb_0'],'f_2':['f_0','f_1','f_0','f_1'],'f_1':['f_2'],'f_4':['f_3','f_0','f_0','f_0','task_1'],'coro_0':['task_0'],'f_3':['f_0','f_1','f_0','f_1'],'filename_2':['filename_0','task_2','limit','limit_10','f_5'],'filename_1':['filename_0','task_2','limit','limit_10','f_5'],'filename_0':['co_0'],'line_1':['line_0','task_2','limit','limit_10','f_5'],'line_2':['line_0','task_2','limit','limit_10','f_5'],'line_0':['filename_0','lineno_0','f_5'],'frames_0':[],'tb_0':['task_1'],'co_1':['co_0','task_2','limit','limit_10','f_5'],'co_2':['co_0','task_2','limit','limit_10','f_5'],'co_0':['f_5'],'info_0':['task_0'],'extracted_list_0':[],}

#added phi names
phi_names_set = {'f_2','limit_3','limit_2','f_3','limit_4','limit_7','tb_2','limit_6','limit_8','tb_3','f_4','limit_9','tb_4','lineno_1','filename_1','line_1','name_1','co_1','lineno_2','filename_2','line_2','name_2','co_2',}