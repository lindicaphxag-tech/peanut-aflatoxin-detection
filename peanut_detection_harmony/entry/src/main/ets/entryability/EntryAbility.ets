import { UIAbility, Want } from '@kit.AbilityKit'
import { window } from '@kit.ArkUI'
import { common } from '@kit.AbilityKit'

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: common.LaunchParam) {
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
