function! s:get_code() abort
  let reg_save = @@
  try
    silent exe 'normal! `[v`]y'
    return @@
  finally
    let @@ = reg_save
  endtry
endfunction

function! ElinOperatorEval(type) abort
  let code = s:get_code()
  let opt = json_encode({'use-base-params': 1})
  return elin#notify('elin.handler.evaluate/evaluate', [code, opt])
endfunction
