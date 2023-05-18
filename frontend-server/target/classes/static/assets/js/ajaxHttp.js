//  通用的ajax方法
function ajaxHttp (obj, callback, err) {
    //修改代码后更换端口为 81 才能继续运行
    //$.ajax(window.location.origin+":9000"+obj.url, {
    //因为：window.location.origin代表 http://localhost:port
    //当 port为 80时，localhost:80:9000相当于 localhost:9000
    //但 port为 81时就无法读取网址，故替换成 hostname变为localhost即可
    $.ajax("http://" + window.location.hostname+":9000"+obj.url, {
        type: obj.type || 'get',
        contentType: obj.contentType || 'application/json;charset=UTF-8',
        headers:{'token': localStorage.getItem('token')},
        data: obj.data || {},
        success: function (res) {
            if (res.code === 200) {
                callback(res)
            } else {
                if (res.code === -2 || res.code === -3) {
                    localStorage.removeItem('token')
                    $('.commodity-header').find('.seckill-shopping').css('display', 'block')
                    setTimeout(function () {
                        layer.confirm('请先进行登录！！', {
                            btn: ['马上登录','取消'] //按钮
                        }, function(){
                            window.location.href = '/login.html'
                        }, function(index){
                            layer.close(index)
                        });
                    }, 200)
                }else{
                    layer.msg(res.msg)
                }
            }
        },
        error: err || defaultError
    })
}
function defaultError(){
    layer.msg('网站繁忙，稍后再试！')
}