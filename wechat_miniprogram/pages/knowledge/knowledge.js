// pages/knowledge/knowledge.js
Page({
  data: {
    flipped: -1,
    toxins: [
      { name: 'AFB1', tag: '毒性最强', desc: '致癌性最高的一类；花生、玉米污染的主要形式，也是国标限量的核心指标。' },
      { name: 'AFB2', tag: 'B1 的衍生物', desc: 'B1 的氢化衍生物，毒性略低，常与 B1 同时检出。' },
      { name: 'AFG1', tag: '绿色荧光', desc: '含内酯环，紫外灯下呈绿色荧光；多见于坚果与香料。' },
      { name: 'AFM1', tag: '乳汁代谢物', desc: 'B1 在动物体内的代谢产物，会进入乳与乳制品。' }
    ]
  },

  toggle(e) {
    const i = Number(e.currentTarget.dataset.index)
    this.setData({ flipped: this.data.flipped === i ? -1 : i })
  },

  onShow() {}
})
