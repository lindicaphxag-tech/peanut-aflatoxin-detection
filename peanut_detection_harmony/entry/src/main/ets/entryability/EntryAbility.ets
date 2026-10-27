import UIAbility from '@ohos.app.ability.UIAbility'
import Want from '@ohos.app.ability.Want'
import window from '@ohos.window'
import common from '@ohos.app.ability.common'
import AbilityConstant from '@ohos.app.ability.AbilityConstant'

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam) {
    console.info('EntryAbility onCreate')
  }

  onDestroy() {
    console.info('EntryAbility onDestroy')
  }

  onWindowStageCreate(windowStage: window.WindowStage) {
    windowStage.loadContent('pages/Index', (err, data) => {
      if (err.code) {
        console.error('Failed to load content.')
        return
      }
      console.info('Succeeded in loading content.')
    })
  }
}
